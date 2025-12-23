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
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class SocketClient {
    private final Gson gson = new Gson();
    private final ConfigManager cfg = new ConfigManager();
    private final SystemMonitor mon = new SystemMonitor();
    private final CommandExecutor exec = new CommandExecutor();
    private final FileDownloader fileDownloader = new FileDownloader("127.0.0.1");
    private final ScreenService screenService;
    private final FileExplorerService fileExplorer = new FileExplorerService();
    private final FileUploader fileUploader = new FileUploader("127.0.0.1");

    private Socket s;
    private PrintWriter out;
    private BufferedReader in;
    private boolean auth = false;
    private volatile boolean isStreaming = false;
    private long sequenceCount = 0;
    private long lastServerSequence = -1;

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
                System.err.println(">> [Client] Mất kết nối, thử lại sau 5 giây...");
                try { Thread.sleep(5000); } catch(Exception ex){}
            }
        }
    }
    //ket noi den server
    private void connect() throws Exception {
        s = new Socket("127.0.0.1", 9999);
        s.setSoTimeout(60000); // NFR2.3: Socket Timeout 60s [cite: 10]
        out = new PrintWriter(s.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        String token = cfg.getToken();
        if (token == null) {
            doOnboard();
        } else {
            doAuth(token);
        }
        if (auth) {
            new Thread(this::heartbeat).start();
            listen();
        }
    }

    //xac thuc lan dau neu chua co token
    private void doOnboard() throws IOException {
        System.out.println(">> Đang thu thập thông tin phần cứng...");
        OnboardingPayload pl = new OnboardingPayload(
                cfg.getKey(),
                HardwareUtil.getMacAddress(),
                HardwareUtil.getHostName(),
                System.getProperty("os.name"),
                HardwareUtil.getCpuName(),
                HardwareUtil.getTotalRam(),
                HardwareUtil.getTotalDiskGb(),
                HardwareUtil.getLocalIpAddress()
        );
        sendSecure("ONBOARDING", null, pl);
        String line = in.readLine();
        NetworkPacket res = decryptAndVerify(line);
        if (res != null && "ONBOARDING_RESPONSE".equals(res.getCommand())) {
            AuthResponse ar = gson.fromJson(res.getPayloadJson(), AuthResponse.class);
            if (ar.isSuccess()) {
                cfg.saveToken(ar.getNewToken());
                s.close();
            }
        }
    }
    //xac thuc hang ngay neu da co token
    private void doAuth(String t) throws IOException {
        sendSecure("AUTH_DAILY", t, null);
        String line = in.readLine();
        if (line != null && line.contains("AUTH_SUCCESS")) {
            auth = true;
            System.out.println(">> [Client] Xác thực hằng ngày thành công.");
        }
    }

    private void listen() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            NetworkPacket p = decryptAndVerify(line);
            if (p == null) continue;

            String cmd = p.getCommand();
            System.out.println(">> [Client] Nhận lệnh bảo mật: " + cmd);
            switch (cmd) {
                case "CMD_SHUTDOWN":
                    exec.shutdown();
                    break;
                case "CMD_RESTART":
                    exec.restart();
                    break;
                case "GET_PROCESSES":
                    sendSecure("PROCESS_LIST_RESPONSE", cfg.getToken(), mon.getProcesses());
                    break;
                case "CMD_KILL_PROCESS":
                    Map data = gson.fromJson(p.getPayloadJson(), Map.class);
                    if (data != null && data.containsKey("pid")) {
                        exec.killProcess(((Double) data.get("pid")).intValue());
                    }
                    break;
                case "GET_FILE_TREE":
                    Map pathData = gson.fromJson(p.getPayloadJson(), Map.class);
                    String path = (pathData != null) ? (String) pathData.get("path") : null;
                    sendSecure("FILE_TREE_RESPONSE", cfg.getToken(), fileExplorer.listFiles(path));
                    break;
                case "CMD_RECEIVE_FILE":
                    FileTransferRequest fileReq = gson.fromJson(p.getPayloadJson(), FileTransferRequest.class);
                    if (fileReq != null) fileDownloader.downloadFile(fileReq);
                    break;
                case "CMD_SEND_FILE_TO_SERVER":
                    Map uploadData = gson.fromJson(p.getPayloadJson(), Map.class);
                    if (uploadData.containsKey("path")) fileUploader.uploadFile((String) uploadData.get("path"));
                    break;
                case "REQ_START_THUMBNAIL_STREAM":
                    startStreamingTask("THUMBNAIL");
                    break; // [cite: 50]
                case "REQ_START_SCREEN_STREAM":
                    startStreamingTask("FULL");
                    break;
                case "REQ_STOP_SCREEN_STREAM":
                    this.isStreaming = false;
                    break;
                case "MIGRATE_GROUP":
                    Map mData = gson.fromJson(p.getPayloadJson(), Map.class);
                    cfg.saveToken((String) mData.get("new_token"));
                    auth = false; s.close();
                    break;
                case "REVOKE_IDENTITY": // [cite: 37]
                    cfg.saveToken(null); auth = false; isStreaming = false; s.close();
                    break;
            }
        }
    }

    //gui heartbeat moi 5s
    private void heartbeat() {
        while (auth && !s.isClosed()) {
            try {
                sendSecure("HEARTBEAT", cfg.getToken(), mon.collect());
                Thread.sleep(5000);
            } catch(Exception e) { break; }
        }
    }
    private void startStreamingTask(String mode) {
        if (isStreaming) isStreaming = false;
        isStreaming = true;
        new Thread(() -> {
            while (isStreaming && !s.isClosed()) {
                try {
                    byte[] imgData = "THUMBNAIL".equals(mode)
                            ? screenService.captureDownscaledAndCompress(320, 180, 0.5f) // [cite: 52]
                            : screenService.captureAndCompress(0.75f);
                    sendSecure("SCREEN_DATA_PUSH", cfg.getToken(), Base64.getEncoder().encodeToString(imgData));
                    Thread.sleep("THUMBNAIL".equals(mode) ? 1000 : 100);
                } catch (Exception e) { isStreaming = false; }
            }
        }).start();
    }
    //gui goi tin bao mat
    private void sendSecure(String command, String token, Object payload) {
        try {
            NetworkPacket p = new NetworkPacket();
            p.setCommand(command);
            p.setToken(token);
            p.setTimestamp(System.currentTimeMillis());
            p.setSequenceNumber(++sequenceCount);
            String jsonPayload = (payload != null) ? gson.toJson(payload) : "";
            p.setPayloadJson(SecurityUtil.encrypt(jsonPayload)); // Mã hóa AES
            String signData = command + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
            p.setHmac(SecurityUtil.generateHMAC(signData)); // Chữ ký HMAC
            synchronized (out) {
                String finalJson = gson.toJson(p);
                if (finalJson.length() > 5 * 1024 * 1024) throw new IOException("Max Packet Size Exceeded");
                out.println(finalJson);
            }
        } catch (Exception e) { System.err.println(">> Lỗi mã hóa gói tin: " + e.getMessage()); }
    }
    //giai ma va xac thuc goi tin nhan duoc
    private NetworkPacket decryptAndVerify(String line) {
        try {
            if (line == null) return null;
            NetworkPacket p = gson.fromJson(line, NetworkPacket.class);
            //xac thuc HMAC
            if (p == null) return null;
            String signData = p.getCommand() + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
            if (!SecurityUtil.generateHMAC(signData).equals(p.getHmac())) return null;
            //kiem tra sequence number
            if (p.getSequenceNumber() <= lastServerSequence) return null;
            lastServerSequence = p.getSequenceNumber();
            //giai ma AES
            if (p.getPayloadJson() != null && !p.getPayloadJson().isEmpty()) {
                p.setPayloadJson(SecurityUtil.decrypt(p.getPayloadJson()));
            }
            return p;
        } catch (Exception e) { return null; }
    }
}