package com.lancontrol.server.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lancontrol.server.gui.FileExplorerController;
import com.lancontrol.server.model.*;
import com.lancontrol.server.network.ClientSession;
import com.lancontrol.server.network.FileServer;
import com.lancontrol.server.network.SessionManager;
import com.lancontrol.server.util.JsonUtil;
import com.lancontrol.server.util.SecurityUtil;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CommandService đóng vai trò là "đầu não" điều phối các lệnh từ UI tới Client
 * và phân phối dữ liệu phản hồi từ Client về các thành phần UI liên quan.
 */
public class CommandService {
    private final SessionManager mgr;
    private final FileServer fileServer;
    private final Gson gson = new Gson();

    private final Map<Integer, FileExplorerController> activeFileExplorers = new HashMap<>();

    private final List<ScreenDataListener> screenListeners = new CopyOnWriteArrayList<>();
    private final List<ProcessDataListener> processListeners = new CopyOnWriteArrayList<>();

    private final ServerScreenService serverScreen;
    private volatile boolean isBroadcasting = false;

    public CommandService(SessionManager m, FileServer fs) {
        this.mgr = m;
        this.fileServer = fs;
        try {
            this.serverScreen = new ServerScreenService();
        } catch (Exception e) {
            throw new RuntimeException("Không khởi tạo được Robot Server", e);
        }
    }


    public void addScreenListener(ScreenDataListener listener) {
        screenListeners.add(listener); //
    }

    public void addProcessListener(ProcessDataListener listener) {
        processListeners.add(listener);
    }
    // gui lenh den client voi co che bao mat
    public void sendSecure(int cid, String cmd, Object payload) {
        ClientSession session = mgr.get(cid);
        if (session == null) return;

        try {
            NetworkPacket p = new NetworkPacket();
            p.setCommand(cmd);
            p.setTimestamp(System.currentTimeMillis());
            p.setSequenceNumber(session.getNextSequence());

            String jsonPayload = (payload != null) ? JsonUtil.toJson(payload) : "";
            p.setPayloadJson(SecurityUtil.encrypt(jsonPayload));

            String signData = cmd + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
            p.setHmac(SecurityUtil.generateHMAC(signData));
            session.send(JsonUtil.toJson(p));

        } catch (Exception e) {
            System.err.println(">> [Error] Lỗi gửi lệnh tới Client " + cid + ": " + e.getMessage());
        }
    }
    public void onScreenFrameReceived(int clientId, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) return;
        for (ScreenDataListener listener : screenListeners) {
            listener.onScreenFrameReceived(clientId, imageBytes);
        }
    }

    public void sendDownloadRequest(int clientId, File localFile) {
        String fileId = UUID.randomUUID().toString();
        fileServer.registerFile(fileId, localFile);

        FileTransferRequest req = new FileTransferRequest(fileId, localFile.getName(), localFile.length());

        sendSecure(clientId, "REQ_DOWNLOAD_FILE", req);
        System.out.println(">> [Server] Đã gửi yêu cầu tải file tới Client " + clientId + ": " + localFile.getName());
    }
    // xu ly phan hoi tu client
    public void handleClientResponse(int clientId, NetworkPacket pkt) {
        String cmd = pkt.getCommand();
        // lay du lieu payload da giai ma
        String jsonPayload = pkt.getPayloadJson();

        switch (cmd) {
            case "PROCESS_LIST_RESPONSE":
                handleProcessData(clientId, jsonPayload);
                break;
            case "FILE_TREE_RESPONSE":
                System.out.println("[INFO] Nhận File Tree từ Client " + clientId);
                System.out.println("[DEBUG] Raw JSON Payload: " + jsonPayload);
                try {
                    Type listType = new TypeToken<List<FileNode>>(){}.getType();
                    List<FileNode> nodes = gson.fromJson(jsonPayload, listType);
                    System.out.println("[DEBUG] Parse thành công " + (nodes != null ? nodes.size() : 0) + " nodes");

                    FileExplorerController fec = activeFileExplorers.get(clientId);
                    System.out.println("[DEBUG] FileExplorerController for client " + clientId + ": " + (fec != null ? "Found" : "NOT FOUND"));

                    if (fec != null) {
                        fec.updateBrowser(nodes);
                        System.out.println("[SUCCESS] Updated browser for client " + clientId);
                    } else {
                        System.out.println("[WARN] No FileExplorerController registered for client " + clientId);
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Parse FILE_TREE_RESPONSE failed: " + e.getMessage());
                    e.printStackTrace();
                }
                break;

            case "PROCESS_KILL_RESPONSE":
                Map res = gson.fromJson(jsonPayload, Map.class);
                String status = (String) res.get("status");
                System.out.println(">> [Kill Status] PID " + res.get("pid") + " : " + status);
                break;
            default:
                System.out.println("[WARN] Lệnh không xác định từ Client: " + cmd);
        }
    }
    // xu ly du lieu hinh anh tu client
    private void handleScreenData(int clientId, String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) return;

        try {

            String cleanData = base64Data.replaceAll("[^A-Za-z0-9+/=]", "");

            byte[] imageBytes = Base64.getDecoder().decode(cleanData);

            for (ScreenDataListener listener : screenListeners) {
                listener.onScreenFrameReceived(clientId, imageBytes);
            }
        } catch (Exception e) {
            System.err.println(">> [Lỗi Base64] Client " + clientId + " vẫn lỗi: " + e.getMessage());
        }
    }
    // xu ly du lieu process tu client
    private void handleProcessData(int clientId, String jsonPayload) {
        try {
            System.out.println(">> [Debug] JSON Process từ Client " + clientId + ": " + jsonPayload);

            Type listType = new TypeToken<List<ProcessInfo>>(){}.getType();
            List<ProcessInfo> processes = gson.fromJson(jsonPayload, listType);

            if (processes != null) {
                System.out.println(">> [Success] Parse được " + processes.size() + " tiến trình.");
                for (ProcessDataListener listener : processListeners) {
                    listener.onProcessListReceived(clientId, processes);
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi phân tích Process List: " + e.getMessage());
        }
    }

    // send lenh shutdown den client
    public void sendShutdown(int cid) {
        Map<String, String> map = new HashMap<>();
        map.put("action", "shutdown");
        sendSecure(cid, "CMD_SHUTDOWN", map); //
    }

    public void requestProcessList(int cid) {
        sendSecure(cid, "GET_PROCESSES", null); //
    }

    public void killProcess(int clientId, int pid) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("pid", pid);
        sendSecure(clientId, "CMD_KILL_PROCESS", payload);
    }

    public void startScreenStream(int clientId, boolean isFullMode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", isFullMode ? "FULL" : "THUMBNAIL");
        sendSecure(clientId, "REQ_START_SCREEN_STREAM", payload);
    }

    public void stopScreenStream(int clientId) {
        sendSecure(clientId, "REQ_STOP_SCREEN_STREAM", null);
    }
    public void sendSleep(int cid) {
        sendSecure(cid, "SLEEP", null);
    }

    public void startBroadcast(int groupId) {
        if (isBroadcasting) return;
        isBroadcasting = true;

        new Thread(() -> {
            System.out.println(">> [Server] Đang Broadcast tới nhóm: " + groupId);
            while (isBroadcasting) {
                try {
                    byte[] imgData = serverScreen.captureAndCompress(0.75f); // Nén JPEG 75%
                    String base64Img = Base64.getEncoder().encodeToString(imgData);

                    for (int cid : mgr.getClientIdsByGroup(groupId)) {
                        sendSecure(cid, "BROADCAST_DATA_PUSH", base64Img);
                    }
                    Thread.sleep(100); // ~10 FPS
                } catch (Exception e) { isBroadcasting = false; }
            }
        }).start();
    }

    public void stopBroadcast() {
        this.isBroadcasting = false;
    }


    public void sendMigrate(int clientId, String newToken) {
        Map<String, String> payload = new HashMap<>();
        payload.put("new_token", newToken);
        sendSecure(clientId, "MIGRATE_GROUP", payload); //
    }

    public void sendRevoke(int clientId) {
        sendSecure(clientId, "REVOKE_IDENTITY", null); //
    }
    public void requestFileUpload(int clientId, String remoteFilePath) {
        sendSecure(clientId, "REQ_UPLOAD_FILE", remoteFilePath);
    }
    public void requestFileTree(int clientId, String path) {
        String payload = (path == null) ? "" : path;
        sendSecure(clientId, "GET_FILE_TREE", payload);
    }

    public void addFileExplorer(int cid, FileExplorerController controller) {
        activeFileExplorers.put(cid, controller);
    }
    public void removeFileExplorerListener(int cid) {
        activeFileExplorers.remove(cid);
    }

}