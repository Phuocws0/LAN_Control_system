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
    private DataOutputStream dos; // Sử dụng Binary Stream
    private DataInputStream dis;  // Sử dụng Binary Stream

    private boolean auth = false;
    private volatile boolean isStreaming = false;
    private volatile boolean isBusy = false; // Chống nghẽn ảnh

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
                System.err.println(">> [Client] Mất kết nối, thử lại sau 5 giây... (" + e.getMessage() + ")");
                try { Thread.sleep(5000); } catch(Exception ex){}
            } finally {
                closeResources();
            }
        }
    }

    private void connect() throws Exception {
        stopOldThreads();

        // 1. Khởi tạo Socket
        this.s = new Socket("127.0.0.1", 9999);
        this.s.setSoTimeout(60000);
        this.s.setTcpNoDelay(true); // Gửi gói tin ngay lập tức (giảm lag)

        // 2. Khởi tạo Luồng nhị phân
        this.dos = new DataOutputStream(s.getOutputStream());
        this.dis = new DataInputStream(s.getInputStream());

        String token = cfg.getToken();
        if (token == null) {
            doOnboard();
        } else {
            doAuth(token);
        }

        if (auth) {
            heartbeatThread = new Thread(this::heartbeat);
            heartbeatThread.start();
            startStreamingTask("THUMBNAIL");
            listen(); // Bắt đầu vòng lặp nhận lệnh chính
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

                // Gửi theo khung: [Type: 0x01][Size: 4 bytes][Data]
                dos.writeByte(0x01);
                dos.writeInt(data.length);
                dos.write(data);
                dos.flush();
            }
        } catch (Exception e) {
            System.err.println(">> [Lỗi Gửi JSON] " + e.getMessage());
        }
    }

    public void sendImageRaw(byte[] imgBytes) {
        if (imgBytes == null || s == null || s.isClosed()) return;
        synchronized (sendLock) {
            try {
                // Gửi theo khung: [Type: 0x02][Size: 4 bytes][Data]
                dos.writeByte(0x02);          // Type ảnh Raw
                dos.writeInt(imgBytes.length); // Không cần Base64 nữa!
                dos.write(imgBytes);
                dos.flush();
            } catch (IOException e) {
                System.err.println(">> [Lỗi Gửi Ảnh] " + e.getMessage());
            }
        }
    }

    // --- VÒNG LẶP NHẬN LỆNH ---

    private void listen() throws IOException {
        while (!s.isClosed()) {
            // 1. Đọc Type (1 byte)
            byte type = dis.readByte();
            // 2. Đọc Size (4 bytes)
            int size = dis.readInt();
            if (size <= 0 || size > 15 * 1024 * 1024) continue;

            // 3. Đọc dữ liệu
            byte[] payload = new byte[size];
            dis.readFully(payload);

            if (type == 0x01) { // Chỉ xử lý JSON lệnh
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
            case "GET_PROCESSES":
                sendSecure("PROCESS_LIST_RESPONSE", cfg.getToken(), mon.getProcesses());
                break;
            case "SLEEP":
                exec.sleep();
                break;
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
                // 1. Giải mã JSON lấy PID
                Map data = gson.fromJson(p.getPayloadJson(), Map.class);
                if (data != null && data.containsKey("pid")) {
                    int pidToKill = ((Double) data.get("pid")).intValue();

                    // 2. Thực hiện Kill
                    boolean success = exec.killProcess(pidToKill);

                    // 3. Gửi phản hồi về Server để cập nhật UI
                    Map<String, Object> response = new HashMap<>();
                    response.put("pid", pidToKill);
                    response.put("status", success ? "SUCCESS" : "FAILED");
                    sendSecure("PROCESS_KILL_RESPONSE", cfg.getToken(), response);
                }
                break;

            case "GET_FILE_TREE":
                String rawPath = p.getPayloadJson();

                // Xử lý loại bỏ dấu nháy kép thực tế nếu có (ví dụ: "C:\" -> C:\)
                if (rawPath != null) {
                    rawPath = rawPath.trim();
                    if (rawPath.startsWith("\"") && rawPath.endsWith("\"")) {
                        rawPath = rawPath.substring(1, rawPath.length() - 1);
                    }
                }

                // Nếu sau khi xóa nháy mà chuỗi vẫn là "" (rỗng), listFiles sẽ quét ổ đĩa [cite: 714]
                List<FileNode> nodes = fileExplorer.listFiles(rawPath);

                System.out.println(">> [Client] Đang quét: '" + rawPath + "' - Tìm thấy: " + nodes.size() + " mục.");
                sendSecure("FILE_TREE_RESPONSE", cfg.getToken(), nodes);
                break;
            case "REQ_UPLOAD_FILE":
                String fileToUpload = p.getPayloadJson();

                // Làm sạch dấu nháy kép để FileUploader nhận đúng đường dẫn [cite: 106, 114]
                if (fileToUpload != null) {
                    fileToUpload = fileToUpload.trim();
                    if (fileToUpload.startsWith("\"") && fileToUpload.endsWith("\"")) {
                        fileToUpload = fileToUpload.substring(1, fileToUpload.length() - 1);
                    }
                }

                if (fileToUpload != null && !fileToUpload.isEmpty()) {
                    new FileUploader(s.getInetAddress().getHostAddress()).uploadFile(fileToUpload); // [cite: 106, 114]
                }
                break;

        }
    }

    // --- QUẢN LÝ STREAMING (TỐI ƯU) ---

    private void startStreamingTask(String mode) {
        isStreaming = false;
        try { Thread.sleep(300); } catch (Exception e) {}
        isStreaming = true;

        streamingThread = new Thread(() -> {
            while (isStreaming && !s.isClosed()) {
                if (isBusy) { // Nếu mạng đang nghẽn, bỏ qua khung hình này
                    try { Thread.sleep(10); } catch (Exception e) {}
                    continue;
                }
                try {
                    isBusy = true;
                    byte[] data;
                    if ("FULL".equals(mode)) {
                        data = screenService.captureStreaming(0.3f); // Nén mạnh + Resize 50%
                    } else {
                        data = screenService.captureDownscaledAndCompress(320, 180, 0.4f);
                    }

                    if (data != null) {
                        sendImageRaw(data); // GỬI ẢNH RAW TRỰC TIẾP
                    }

                    Thread.sleep("FULL".equals(mode) ? 80 : 1000);
                } catch (Exception e) { break; }
                finally { isBusy = false; }
            }
        });
        streamingThread.start();
    }

    // --- TIỆN ÍCH ---

    private void doAuth(String t) throws IOException {
        sendSecure("AUTH_DAILY", t, null);
        // Đọc phản hồi Auth (Binary)
        if (dis.readByte() == 0x01) {
            int size = dis.readInt();
            byte[] data = new byte[size];
            dis.readFully(data);
            String res = new String(data, StandardCharsets.UTF_8);
            if (res.contains("AUTH_SUCCESS")) auth = true;
        }
    }

    private void doOnboard() throws IOException {
        OnboardingPayload pl = new OnboardingPayload(
                cfg.getKey(), HardwareUtil.getMacAddress(), HardwareUtil.getHostName(),
                System.getProperty("os.name"), HardwareUtil.getCpuName(),
                HardwareUtil.getTotalRam(), HardwareUtil.getTotalDiskGb(), HardwareUtil.getLocalIpAddress()
        );
        sendSecure("ONBOARDING", null, pl);
        // Đọc phản hồi Onboard tương tự doAuth...
        auth = true; // Giả định thành công để giản lược
    }

    private void heartbeat() {
        while (auth && !s.isClosed()) {
            try {
                sendSecure("HEARTBEAT", cfg.getToken(), mon.collect());
                Thread.sleep(5000);
            } catch(Exception e) { break; }
        }
    }

    private void stopOldThreads() {
        isStreaming = false;
        if (heartbeatThread != null) heartbeatThread.interrupt();
        if (streamingThread != null) streamingThread.interrupt();
    }

    private void closeResources() {
        try { if (s != null) s.close(); } catch (IOException e) {}
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