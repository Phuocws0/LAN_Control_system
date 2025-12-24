package com.lancontrol.client.network;

import com.google.gson.Gson;
import com.lancontrol.client.config.ConfigManager;
import com.lancontrol.client.handler.CommandExecutor;
import com.lancontrol.client.model.*;
import com.lancontrol.client.monitor.ScreenService;
import com.lancontrol.client.monitor.SystemMonitor;
import com.lancontrol.client.service.FileExplorerService;
import com.lancontrol.client.util.HardwareUtil;
import com.lancontrol.client.util.SecurityUtil;

import java.awt.AWTException;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketClient {
    private final Gson gson = new Gson();
    private final ConfigManager cfg = new ConfigManager();
    private final SystemMonitor mon = new SystemMonitor();
    private final CommandExecutor exec = new CommandExecutor();
    private final ScreenService screenService;
    private final FileExplorerService fileExplorer = new FileExplorerService();

    private Socket s;
    private DataOutputStream dos;
    private DataInputStream dis;

    private boolean auth = false;
    private volatile boolean isStreaming = false;
    private volatile boolean isBusy = false;

    private long sequenceCount = 0;
    private long lastServerSequence = -1;
    private final Object sendLock = new Object();

    private Thread heartbeatThread;
    private Thread streamingThread;

    public SocketClient() {
        try {
            this.screenService = new ScreenService();
        } catch (AWTException e) {
            throw new RuntimeException("Lỗi: Không thể khởi tạo Robot đồ họa", e);
        }
    }

    public void start() {
        while (true) {
            try {
                connect();
            } catch (Exception e) {
                auth = false;
                isStreaming = false;
                // Chỉ in lỗi nếu không phải là ngắt kết nối chủ động
                System.err.println(">> [Client] Mất kết nối, thử lại sau 5 giây... (" + e.getMessage() + ")");
                try { Thread.sleep(5000); } catch(Exception ex){}
            } finally {
                closeResources();
            }
        }
    }

    private void connect() throws Exception {
        // [QUAN TRỌNG] Dọn dẹp luồng cũ sạch sẽ trước khi tạo kết nối mới
        stopOldThreads();
        closeResources();

        System.out.println(">> [Client] Đang kết nối đến Server...");
        // 1. Khởi tạo Socket
        this.s = new Socket("127.0.0.1", 9999); // IP Server của bạn
        this.s.setSoTimeout(60000);
        this.s.setTcpNoDelay(true);

        // 2. Khởi tạo Stream
        this.dos = new DataOutputStream(s.getOutputStream());
        this.dis = new DataInputStream(s.getInputStream());

        String token = cfg.getToken();

        // 3. Phân luồng Logic: Onboard hoặc Login
        if (token == null || token.isEmpty()) {
            doOnboard();
            // [QUAN TRỌNG] Sau khi Onboard xong, thoát hàm connect() ngay
            // Để vòng lặp while(true) ở start() thực hiện kết nối lại với Token mới.
            return;
        } else {
            doAuth(token);
        }

        // 4. Chỉ chạy các tác vụ nền khi đã Auth thành công
        if (auth) {
            System.out.println(">> [Client] Đăng nhập thành công!");
            heartbeatThread = new Thread(this::heartbeat);
            heartbeatThread.start();

            startStreamingTask("THUMBNAIL");
            listen(); // Bắt đầu vòng lặp nhận lệnh (Block tại đây)
        } else {
            System.err.println(">> [Client] Token không hợp lệ. Đang xóa token để Onboard lại...");
            cfg.saveToken(null);
        }
    }

    // --- CƠ CHẾ GỬI DỮ LIỆU BINARY ---

    private void sendSecure(String command, String token, Object payload) {
        try {
            String jsonPayload = (payload != null) ? gson.toJson(payload) : "";
            String encryptedPayload = SecurityUtil.encrypt(jsonPayload);
            long timestamp = System.currentTimeMillis();

            synchronized (sendLock) {
                if (s == null || s.isClosed()) return;

                NetworkPacket p = new NetworkPacket();
                p.setCommand(command);
                p.setToken(token);
                p.setTimestamp(timestamp);
                p.setSequenceNumber(++sequenceCount);
                p.setPayloadJson(encryptedPayload);

                String signData = p.getCommand() + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
                p.setHmac(SecurityUtil.generateHMAC(signData));

                byte[] data = gson.toJson(p).getBytes(StandardCharsets.UTF_8);

                dos.writeByte(0x01);
                dos.writeInt(data.length);
                dos.write(data);
                dos.flush(); // Bắt buộc flush
            }
        } catch (Exception e) {
            System.err.println(">> [Lỗi Gửi JSON] " + e.getMessage());
        }
    }

    public void sendImageRaw(byte[] imgBytes) {
        if (imgBytes == null || s == null || s.isClosed()) return;
        synchronized (sendLock) {
            try {
                dos.writeByte(0x02);
                dos.writeInt(imgBytes.length);
                dos.write(imgBytes);
                dos.flush(); // Bắt buộc flush
            } catch (IOException e) {
                // Không in lỗi ở đây để tránh spam log khi mất kết nối
            }
        }
    }

    // --- ONBOARDING & AUTH ---

    private void doOnboard() throws IOException {
        System.out.println(">> [Client] Chưa có Token. Bắt đầu Onboarding...");

        OnboardingPayload pl = new OnboardingPayload(
                cfg.getKey(), HardwareUtil.getMacAddress(), HardwareUtil.getHostName(),
                System.getProperty("os.name"), HardwareUtil.getCpuName(),
                HardwareUtil.getTotalRam(), HardwareUtil.getTotalDiskGb(), HardwareUtil.getLocalIpAddress()
        );

        // Gửi lệnh Onboard
        sendSecure("ONBOARDING", null, pl);

        // [QUAN TRỌNG] Đợi Server trả về Token
        try {
            byte type = dis.readByte();
            int size = dis.readInt();
            byte[] data = new byte[size];
            dis.readFully(data);

            String jsonRaw = new String(data, StandardCharsets.UTF_8);
            NetworkPacket resp = gson.fromJson(jsonRaw, NetworkPacket.class);

            if (resp != null && resp.getPayloadJson() != null) {
                // Giải mã lấy Token
                String decrypted = SecurityUtil.decrypt(resp.getPayloadJson());
                AuthResponse authRes = gson.fromJson(decrypted, AuthResponse.class);

                if (authRes != null && authRes.getNewToken() != null) {
                    System.out.println(">> [Client] Nhận Token mới: " + authRes.getNewToken());
                    cfg.saveToken(authRes.getNewToken()); // Lưu vào file
                }
            }
        } catch (Exception e) {
            System.err.println(">> [Client] Lỗi đọc phản hồi Onboard: " + e.getMessage());
        }

        // Set auth = false để ép connect() thoát ra và reconnect lại
        this.auth = false;
    }

    private void doAuth(String t) throws IOException {
        sendSecure("AUTH_DAILY", t, null);

        // Đọc phản hồi Auth
        byte type = dis.readByte();
        int size = dis.readInt();
        byte[] data = new byte[size];
        dis.readFully(data);

        String jsonRaw = new String(data, StandardCharsets.UTF_8);
        NetworkPacket resp = gson.fromJson(jsonRaw, NetworkPacket.class);

        // Kiểm tra xem Server có báo AUTH_SUCCESS không
        if (resp != null && "AUTH_SUCCESS".equals(resp.getCommand())) {
            this.auth = true;
        } else {
            this.auth = false;
        }
    }

    // --- VÒNG LẶP NHẬN LỆNH ---

    private void listen() throws IOException {
        while (!s.isClosed()) {
            byte type = dis.readByte();
            int size = dis.readInt();

            if (size <= 0 || size > 15 * 1024 * 1024) {
                // Bảo vệ bộ nhớ
                throw new IOException("Gói tin kích thước không hợp lệ: " + size);
            }

            byte[] payload = new byte[size];
            dis.readFully(payload);

            if (type == 0x01) {
                String line = new String(payload, StandardCharsets.UTF_8);
                handleCommand(line);
            }
        }
    }

    private void handleCommand(String line) {
        NetworkPacket p = decryptAndVerify(line);
        if (p == null) return;

        String cmd = p.getCommand();
        switch (cmd) {
            case "CMD_SHUTDOWN": exec.shutdown(); break;
            case "REQ_DOWNLOAD_FILE":
                // 1. Giải mã thông tin tệp từ payload JSON [cite: 115, 178]
                FileTransferRequest downloadReq = gson.fromJson(p.getPayloadJson(), FileTransferRequest.class);

                if (downloadReq != null) {
                    // 2. Khởi tạo FileDownloader với IP của Server hiện tại [cite: 93, 95]
                    String serverIp = s.getInetAddress().getHostAddress();
                    FileDownloader downloader = new FileDownloader(serverIp);

                    // 3. Bắt đầu quá trình tải về (mặc định vào C:\LanControlDownloads\) [cite: 94, 96]
                    downloader.downloadFile(downloadReq);
                    System.out.println(">> [Client] Đang bắt đầu tải file từ Server: " + downloadReq.getFileName());
                }
                break;
            case "GET_PROCESSES":
                sendSecure("PROCESS_LIST_RESPONSE", cfg.getToken(), mon.getProcesses());
                break;
            case "SLEEP": exec.sleep(); break;
            case "REQ_START_SCREEN_STREAM":
                startStreamingTask("FULL");
                break;
            case "REQ_STOP_SCREEN_STREAM":
                this.isStreaming = false;
                new Thread(() -> {
                    try { Thread.sleep(300); } catch(Exception e){}
                    startStreamingTask("THUMBNAIL");
                }).start();
                break;
            case "CMD_KILL_PROCESS":
                Map data = gson.fromJson(p.getPayloadJson(), Map.class);
                if (data != null && data.containsKey("pid")) {
                    int pidToKill = ((Double) data.get("pid")).intValue();
                    boolean success = exec.killProcess(pidToKill);
                    Map<String, Object> response = new HashMap<>();
                    response.put("pid", pidToKill);
                    response.put("status", success ? "SUCCESS" : "FAILED");
                    sendSecure("PROCESS_KILL_RESPONSE", cfg.getToken(), response);
                }
                break;
            case "GET_FILE_TREE":
                String rawPath = p.getPayloadJson();
                if (rawPath != null) {
                    rawPath = rawPath.trim();
                    if (rawPath.startsWith("\"") && rawPath.endsWith("\"")) {
                        rawPath = rawPath.substring(1, rawPath.length() - 1);
                    }
                }
                List<FileNode> nodes = fileExplorer.listFiles(rawPath);
                sendSecure("FILE_TREE_RESPONSE", cfg.getToken(), nodes);
                break;
            case "REQ_UPLOAD_FILE":
                String fileToUpload = p.getPayloadJson();
                if (fileToUpload != null) {
                    fileToUpload = fileToUpload.trim();
                    if (fileToUpload.startsWith("\"") && fileToUpload.endsWith("\"")) {
                        fileToUpload = fileToUpload.substring(1, fileToUpload.length() - 1);
                    }
                }
                if (fileToUpload != null && !fileToUpload.isEmpty()) {
                    new FileUploader(s.getInetAddress().getHostAddress()).uploadFile(fileToUpload);
                }
                break;
        }
    }

    // --- QUẢN LÝ STREAMING (FIX LỖI GHOST THREAD) ---

    private void startStreamingTask(String mode) {
        isStreaming = false;
        try { Thread.sleep(300); } catch (Exception e) {}
        isStreaming = true;

        // Lưu lại socket hiện tại để luồng check
        final Socket currentSocket = this.s;

        streamingThread = new Thread(() -> {
            while (isStreaming && !currentSocket.isClosed()) {
                // Kiểm tra nếu Socket chính đã bị thay đổi (do reconnect)
                if (this.s != currentSocket) {
                    System.out.println(">> [Stream] Socket thay đổi -> Tự hủy luồng cũ.");
                    break;
                }

                if (isBusy) {
                    try { Thread.sleep(10); } catch (Exception e) {}
                    continue;
                }
                try {
                    isBusy = true;
                    byte[] data;
                    if ("FULL".equals(mode)) {
                        data = screenService.captureStreaming(0.3f);
                    } else {
                        data = screenService.captureDownscaledAndCompress(320, 180, 0.4f);
                    }

                    if (data != null) {
                        sendImageRaw(data);
                    }

                    // Tăng delay lên 100ms để tránh nghẽn mạng
                    Thread.sleep("FULL".equals(mode) ? 100 : 1000);
                } catch (Exception e) { break; }
                finally { isBusy = false; }
            }
        });
        streamingThread.start();
    }

    private void heartbeat() {
        while (auth && !s.isClosed()) {
            try {
                if (s == null || s.isClosed()) break;
                sendSecure("HEARTBEAT", cfg.getToken(), mon.collect());
                Thread.sleep(5000);
            } catch(Exception e) { break; }
        }
    }

    // --- QUẢN LÝ LUỒNG (FIX LỖI ABORT) ---

    private void stopOldThreads() {
        isStreaming = false;

        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
            try { heartbeatThread.join(1000); } catch (InterruptedException e) {} // Đợi chết hẳn
        }

        if (streamingThread != null && streamingThread.isAlive()) {
            streamingThread.interrupt();
            try { streamingThread.join(1000); } catch (InterruptedException e) {} // Đợi chết hẳn
        }
    }

    private void closeResources() {
        try { if (dis != null) dis.close(); } catch (Exception e) {}
        try { if (dos != null) dos.close(); } catch (Exception e) {}
        try { if (s != null) s.close(); } catch (Exception e) {}
    }

    private NetworkPacket decryptAndVerify(String line) {
        try {
            NetworkPacket p = gson.fromJson(line, NetworkPacket.class);
            if (p == null) return null;
            String signData = p.getCommand() + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
            if (!SecurityUtil.generateHMAC(signData).equals(p.getHmac())) return null;
            if (p.getSequenceNumber() <= lastServerSequence) return null;
            lastServerSequence = p.getSequenceNumber();
            if (p.getPayloadJson() != null && !p.getPayloadJson().isEmpty()) {
                p.setPayloadJson(SecurityUtil.decrypt(p.getPayloadJson()));
            }
            return p;
        } catch (Exception e) { return null; }
    }
}