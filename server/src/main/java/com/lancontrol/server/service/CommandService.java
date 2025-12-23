package com.lancontrol.server.service;

import com.lancontrol.server.model.*;
import com.lancontrol.server.network.ClientSession;
import com.lancontrol.server.network.FileServer;
import com.lancontrol.server.network.SessionManager;
import com.lancontrol.server.util.JsonUtil;
import com.lancontrol.server.util.SecurityUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class CommandService {
    private final SessionManager mgr;
    private final FileServer fileServer;
    private final List<ScreenDataListener> screenListeners = new CopyOnWriteArrayList<>();
    private final ServerScreenService serverScreen;
    private volatile boolean isBroadcasting = false;

    public CommandService(SessionManager m, FileServer fs) {
        this.mgr = m;
        this.fileServer = fs;
        try {
            // Khởi tạo dịch vụ chụp màn hình Server phục vụ Broadcast [cite: 45]
            this.serverScreen = new ServerScreenService();
        } catch (Exception e) {
            throw new RuntimeException("Không khởi tạo được Robot Server", e);
        }
    }

    // --- CƠ CHẾ GỬI BẢO MẬT TRUNG TÂM (NFR2.1 & NFR2.2) ---

    /**
     * Hàm bọc bảo mật cho mọi lệnh gửi từ Server tới Client.
     * Thực hiện: Mã hóa AES, Ký HMAC và Gán Sequence Number.
     */
    private void sendSecure(int cid, String cmd, Object payload) {
        ClientSession session = mgr.get(cid);
        if (session == null) return;

        try {
            NetworkPacket p = new NetworkPacket();
            p.setCommand(cmd);
            p.setTimestamp(System.currentTimeMillis());
            p.setSequenceNumber(session.getNextSequence()); // Chống Replay Attack

            // Gán token xác thực giao dịch nếu cần (NFR2.2) [cite: 9]
            // p.setToken(...);

            // 1. Mã hóa AES cho Payload
            String jsonPayload = (payload != null) ? JsonUtil.toJson(payload) : "";
            p.setPayloadJson(SecurityUtil.encrypt(jsonPayload));

            // 2. Ký HMAC để đảm bảo tính toàn vẹn dữ liệu
            String signData = cmd + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
            p.setHmac(SecurityUtil.generateHMAC(signData));

            // 3. Gửi gói tin JSON đã bảo mật qua Socket [cite: 3]
            session.send(JsonUtil.toJson(p));

        } catch (Exception e) {
            System.err.println(">> [Error] Lỗi bảo mật khi gửi lệnh tới Client " + cid + ": " + e.getMessage());
        }
    }

    // --- MODULE 4: TÁC VỤ & ĐIỀU KHIỂN TỪ XA [cite: 38] ---

    public void sendShutdown(int cid) {
        Map<String, String> map = new HashMap<>();
        map.put("action", "shutdown");
        sendSecure(cid, "CMD_SHUTDOWN", map); // [cite: 39]
    }

    public void requestProcessList(int cid) {
        sendSecure(cid, "GET_PROCESSES", null); // [cite: 40]
    }

    public void killProcess(int clientId, int pid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pid", pid);
        sendSecure(clientId, "CMD_KILL_PROCESS", payload);
    }

    public void requestFileTree(int clientId, String path) {
        Map<String, String> payload = new HashMap<>();
        payload.put("path", path);
        sendSecure(clientId, "GET_FILE_TREE", payload); // [cite: 42]
    }

    public void sendFile(int clientId, File file) {
        if (!file.exists()) return;
        String fileId = UUID.randomUUID().toString();
        fileServer.registerFile(fileId, file); // Đăng ký file với Byte Stream server [cite: 43]

        FileTransferRequest req = new FileTransferRequest(fileId, file.getName(), file.length());
        sendSecure(clientId, "CMD_RECEIVE_FILE", req);
    }

    public void requestFileDownloadFromClient(int clientId, String clientFilePath) {
        Map<String, String> payload = new HashMap<>();
        payload.put("path", clientFilePath);
        sendSecure(clientId, "CMD_SEND_FILE_TO_SERVER", payload);
    }

    // --- MODULE 5: GIÁM SÁT MÀN HÌNH [cite: 49] ---

    public void startScreenStream(int clientId, boolean isSingleView) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", isSingleView ? "CONTINUOUS" : "EVENT_DRIVEN");
        payload.put("fps", isSingleView ? 20 : 1); // [cite: 54]

        sendSecure(clientId, "REQ_START_SCREEN_STREAM", payload);
    }

    public void stopScreenStream(int clientId) {
        sendSecure(clientId, "REQ_STOP_SCREEN_STREAM", null);
    }

    public void startGroupThumbnailStream(int groupId) {
        // Duyệt danh sách máy online từ Session Manager [cite: 48]
        for (int clientId : mgr.getClientIdsByGroup(groupId)) {
            sendSecure(clientId, "REQ_START_THUMBNAIL_STREAM", null); // [cite: 50]
        }
    }

    public void stopGroupThumbnailStream(int groupId) {
        for (int clientId : mgr.getClientIdsByGroup(groupId)) {
            sendSecure(clientId, "REQ_STOP_THUMBNAIL_STREAM", null); // [cite: 53]
        }
    }

    // --- MODULE 3: QUẢN LÝ NHÓM (MIGRATE & REVOKE) [cite: 33] ---

    public void sendMigrate(int clientId, String newToken) {
        Map<String, String> payload = new HashMap<>();
        payload.put("new_token", newToken);
        sendSecure(clientId, "MIGRATE_GROUP", payload); // [cite: 36]
    }

    public void sendRevoke(int clientId) {
        sendSecure(clientId, "REVOKE_IDENTITY", null); // [cite: 37]
    }

    // --- TÁC VỤ BROADCAST [cite: 44] ---

    public void startBroadcast(int groupId) {
        if (isBroadcasting) return;
        isBroadcasting = true;

        new Thread(() -> {
            System.out.println(">> [Server] Bắt đầu Broadcast màn hình tới nhóm: " + groupId);
            while (isBroadcasting) {
                try {
                    // Chụp và nén JPEG 70-80% [cite: 45]
                    byte[] imgData = serverScreen.captureAndCompress(0.75f);
                    String base64Img = Base64.getEncoder().encodeToString(imgData);

                    List<Integer> clientIds = mgr.getClientIdsByGroup(groupId);
                    for (int cid : clientIds) {
                        // Gửi mã hóa riêng cho từng Client để đảm bảo Sequence Number chính xác
                        sendSecure(cid, "BROADCAST_DATA_PUSH", base64Img);
                    }
                    Thread.sleep(100); // 10 FPS
                } catch (Exception e) { isBroadcasting = false; }
            }
        }).start();
    }

    public void stopBroadcast() {
        this.isBroadcasting = false;
    }
    public void handleClientResponse(int clientId, NetworkPacket pkt) {
        String cmd = pkt.getCommand();
        switch (cmd) {
            case "SCREEN_DATA_PUSH":
                handleScreenData(clientId, pkt.getPayloadJson());
                break;
            case "PROCESS_LIST_RESPONSE":
                System.out.println("[INFO] Nhận Process List từ Client " + clientId);
                break;
            case "FILE_TREE_RESPONSE":
                System.out.println("[INFO] Nhận File Tree từ Client " + clientId);
                break;
            default:
                System.out.println("[WARN] Lệnh không xác định: " + cmd);
        }
    }

    private void handleScreenData(int clientId, String base64Data) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            for (ScreenDataListener listener : screenListeners) {
                listener.onScreenFrameReceived(clientId, imageBytes);
            }
        } catch (Exception e) {
            System.err.println("Lỗi xử lý ảnh: " + e.getMessage());
        }
    }

    public void addScreenListener(ScreenDataListener listener) {
        screenListeners.add(listener);
    }
}