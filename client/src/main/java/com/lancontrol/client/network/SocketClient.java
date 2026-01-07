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
                System.err.println(">> [Client] Mất kết nối, thử lại sau 5 giây... (" + e.getMessage() + ")");
                try { Thread.sleep(5000); } catch(Exception ex){}
            } finally {
                closeResources();
            }
        }
    }

    private void connect() throws Exception {
        stopOldThreads();
        closeResources();
        System.out.println(">> [Client] Đang kết nối đến Server..." + cfg.getServerIp());
        this.s = new Socket(cfg.getServerIp(), 9999); // IP Server của bạn
        this.s.setSoTimeout(60000);
        this.s.setTcpNoDelay(true);
        this.dos = new DataOutputStream(s.getOutputStream());
        this.dis = new DataInputStream(s.getInputStream());

        String token = cfg.getToken();
        if (token == null || token.isEmpty()) {
            doOnboard();
            return;
        } else {
            doAuth(token);
        }
        if (auth) {
            System.out.println(">> [Client] Đăng nhập thành công!");
            heartbeatThread = new Thread(this::heartbeat);
            heartbeatThread.start();

            startStreamingTask("THUMBNAIL");
            listen();
        } else {
            System.err.println(">> [Client] Token không hợp lệ. Đang xóa token để Onboard lại...");
            cfg.saveToken(null);
        }
    }
    public void close() {
        this.auth = false;
        try {
            if (s != null && !s.isClosed()) {
                s.close();
            }
        } catch (Exception e) {}
    }
    // gui lenh ma hoa
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
                dos.writeByte(0x02);
                dos.writeInt(imgBytes.length);
                dos.write(imgBytes);
                dos.flush();
            } catch (IOException e) {

            }
        }
    }


    // xac thuc va dang ky
    private void doOnboard() throws IOException {
        System.out.println(">> [Client] Chưa có Token. Bắt đầu Onboarding...");
        // tao payload onboarding
        OnboardingPayload pl = new OnboardingPayload(
                cfg.getKey(), HardwareUtil.getMacAddress(), HardwareUtil.getHostName(),
                System.getProperty("os.name"), HardwareUtil.getCpuName(),
                HardwareUtil.getTotalRam(), HardwareUtil.getTotalDiskGb(), HardwareUtil.getLocalIpAddress()
        );

        //gui lenh onboard
        sendSecure("ONBOARDING", null, pl);

        // doc phan hoi onboard
        try {
            byte type = dis.readByte();
            int size = dis.readInt();
            byte[] data = new byte[size];
            dis.readFully(data);

            String jsonRaw = new String(data, StandardCharsets.UTF_8);
            NetworkPacket resp = gson.fromJson(jsonRaw, NetworkPacket.class);

            if (resp != null && resp.getPayloadJson() != null) {
                //giai ma phan hoi
                String decrypted = SecurityUtil.decrypt(resp.getPayloadJson());
                AuthResponse authRes = gson.fromJson(decrypted, AuthResponse.class);

                if (authRes != null && authRes.getNewToken() != null) {
                    System.out.println(">> [Client] Nhận Token mới: " + authRes.getNewToken());
                    cfg.saveToken(authRes.getNewToken()); //luu token
                }
            }
        } catch (Exception e) {
            System.err.println(">> [Client] Lỗi đọc phản hồi Onboard: " + e.getMessage());
        }

        this.auth = false;
    }
    // xac thuc voi token da co
    private void doAuth(String t) throws IOException {
        sendSecure("AUTH_DAILY", t, null);
        // doc phan hoi auth
        byte type = dis.readByte();
        int size = dis.readInt();
        byte[] data = new byte[size];
        dis.readFully(data);

        String jsonRaw = new String(data, StandardCharsets.UTF_8);
        NetworkPacket resp = gson.fromJson(jsonRaw, NetworkPacket.class);

        if (resp != null) {
            if ("AUTH_SUCCESS".equals(resp.getCommand())) {
                this.auth = true;
                return; // xac thuc thanh cong
            } else if ("AUTH_FAILED".equals(resp.getCommand())) {
                // Token khong dung
                System.err.println(">> [Client] Server báo Token không đúng -> Xóa để đăng ký lại.");
                cfg.saveToken(null);
                this.auth = false;
                return;
            }
        }

        this.auth = false;
        System.err.println(">> [Client] Auth thất bại do lỗi mạng hoặc Server, giữ lại Token thử lại sau.");
    }

    // lang nghe lenh tu server
    private void listen() throws IOException {
        while (!s.isClosed()) {
            byte type = dis.readByte();
            int size = dis.readInt();

            if (size <= 0 || size > 15 * 1024 * 1024) {
                // Gioi han kich thuoc goi tin 15MB
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
    // xu ly lenh tu server
    private void handleCommand(String line) {
        NetworkPacket p = decryptAndVerify(line);
        if (p == null) return;

        String cmd = p.getCommand();
        switch (cmd) {
            case "CMD_SHUTDOWN": exec.shutdown(); break;
            case "REQ_DOWNLOAD_FILE":
                FileTransferRequest downloadReq = gson.fromJson(p.getPayloadJson(), FileTransferRequest.class);
                if (downloadReq != null) {
                    String serverIp = s.getInetAddress().getHostAddress();
                    FileDownloader downloader = new FileDownloader(serverIp);
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

    // luong truyen anh
    private void startStreamingTask(String mode) {
        isStreaming = false;
        try { Thread.sleep(300); } catch (Exception e) {}
        isStreaming = true;
        final Socket currentSocket = this.s;
        streamingThread = new Thread(() -> {
            while (isStreaming && !currentSocket.isClosed()) {
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
                    // dieu chinh toc do truyen
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
    // dung luong cu neu co
    private void stopOldThreads() {
        isStreaming = false;

        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
            try { heartbeatThread.join(1000); } catch (InterruptedException e) {}
        }

        if (streamingThread != null && streamingThread.isAlive()) {
            streamingThread.interrupt();
            try { streamingThread.join(1000); } catch (InterruptedException e) {}
        }
    }
    // dong cac resource
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