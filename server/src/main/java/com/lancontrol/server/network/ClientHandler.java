package com.lancontrol.server.network;

import com.lancontrol.server.db.ClientDeviceDAO;
import com.lancontrol.server.model.*;
import com.lancontrol.server.service.*;
import com.lancontrol.server.util.JsonUtil;
import com.lancontrol.server.util.SecurityUtil;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final SessionManager sessionMgr;
    private final AuthService authService;
    private final HeartbeatService hbService;
    private final CommandService commandService;

    private ClientSession session;
    private DataOutputStream dos;
    private DataInputStream dis;

    private long lastClientSequence = -1;
    private long serverSequence = 0;
    private final Object sendLock = new Object();

    public ClientHandler(Socket s, SessionManager sm, AuthService as, HeartbeatService hs, CommandService cs) {
        this.socket = s;
        this.sessionMgr = sm;
        this.authService = as;
        this.hbService = hs;
        this.commandService = cs;
    }

    @Override
    public void run() {
        try {
            // Khởi tạo luồng nhị phân
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            socket.setSoTimeout(60000);

            // 1. XỬ LÝ XÁC THỰC BAN ĐẦU (Theo giao thức Binary)
            if (!handleHandshake()) return;

            // 2. VÒNG LẶP NHẬN DỮ LIỆU LIÊN TỤC
            while (!socket.isClosed()) {
                // Đọc Type (1 byte)
                byte type = dis.readByte();

                // Đọc Size (4 bytes)
                int size = dis.readInt();
                if (size <= 0 || size > 20 * 1024 * 1024) {
                    throw new IOException("Kích thước gói tin không hợp lệ: " + size);
                }

                // Đọc dữ liệu Payload
                byte[] payload = new byte[size];
                dis.readFully(payload);

                if (type == 0x01) {
                    // Type 0x01: Gói tin JSON (Lệnh, Heartbeat)
                    String json = new String(payload, StandardCharsets.UTF_8);
                    handleJsonPacket(json);
                } else if (type == 0x02) {
                    // Type 0x02: ẢNH RAW (Không qua Base64/AES - Cực nhanh)
                    if (session != null) {
                        commandService.onScreenFrameReceived(session.getClientId(), payload);
                    }
                }
            }
        } catch (EOFException e) {
            System.out.println(">> [ClientHandler] Client ngắt kết nối chủ động.");
        } catch (Exception e) {
            System.err.println(">> [ClientHandler] Lỗi kết nối: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private boolean handleHandshake() throws Exception {
        // Đọc gói tin đầu tiên (luôn là Type 0x01 - JSON)
        byte type = dis.readByte();
        int size = dis.readInt();
        byte[] data = new byte[size];
        dis.readFully(data);

        NetworkPacket p = decryptAndVerify(new String(data, StandardCharsets.UTF_8));
        if (p == null) return false;

        if ("ONBOARDING".equals(p.getCommand())) {
            OnboardingPayload pl = JsonUtil.fromJson(p.getPayloadJson(), OnboardingPayload.class);
            AuthResponse resp = authService.processOnboarding(pl, socket.getInetAddress().getHostAddress());
            sendSecure("ONBOARDING_RESPONSE", null, resp);
            return false; // Onboarding xong client sẽ kết nối lại
        } else if ("AUTH_DAILY".equals(p.getCommand())) {
            ClientDevice dev = authService.getClientByToken(p.getToken());
            if (dev != null) {
                // Khởi tạo Session
                session = new ClientSession(dev.getClientId(), dev.getGroupId(), socket, null); // Chú ý: Cần cập nhật ClientSession dùng DataOutputStream
                session.setHostname(dev.getClientName());
                session.setMacAddress(dev.getMacAddress());
                session.setIpAddress(socket.getInetAddress().getHostAddress());

                sessionMgr.add(session);
                sendSecure("AUTH_SUCCESS", null, "Access Granted");
                System.out.println(">> [Server] Thiết bị " + dev.getClientName() + " (ID: " + dev.getClientId() + ") đã kết nối.");
                return true;
            }
        }
        return false;
    }

    private void handleJsonPacket(String json) {
        NetworkPacket pkt = decryptAndVerify(json);
        if (pkt == null) return;

        if ("HEARTBEAT".equals(pkt.getCommand())) {
            HeartbeatModel hb = JsonUtil.fromJson(pkt.getPayloadJson(), HeartbeatModel.class);
            hbService.processHeartbeat(session.getClientId(), hb);
        } else {
            // Các phản hồi lệnh khác (Process List, File...)
            commandService.handleClientResponse(session.getClientId(), pkt);
        }
    }

    // Gửi dữ liệu bảo mật dạng Binary
    public void sendSecure(String cmd, String token, Object payload) {
        try {
            NetworkPacket p = new NetworkPacket();
            p.setCommand(cmd);
            p.setToken(token);
            p.setTimestamp(System.currentTimeMillis());
            p.setSequenceNumber(++serverSequence);

            String jsonPayload = (payload instanceof String) ? (String) payload : JsonUtil.toJson(payload);
            p.setPayloadJson(SecurityUtil.encrypt(jsonPayload));

            String signData = cmd + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
            p.setHmac(SecurityUtil.generateHMAC(signData));

            byte[] finalData = JsonUtil.toJson(p).getBytes(StandardCharsets.UTF_8);

            synchronized (sendLock) {
                dos.writeByte(0x01); // Type JSON
                dos.writeInt(finalData.length);
                dos.write(finalData);
                dos.flush();
            }
        } catch (Exception e) {
            System.err.println(">> [Security] Lỗi gửi tin: " + e.getMessage());
        }
    }

    private NetworkPacket decryptAndVerify(String line) {
        try {
            NetworkPacket p = JsonUtil.fromJson(line, NetworkPacket.class);
            if (p == null) return null;
            String signData = p.getCommand() + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
            if (!SecurityUtil.generateHMAC(signData).equals(p.getHmac())) return null;

            if (p.getSequenceNumber() <= lastClientSequence) return null;
            lastClientSequence = p.getSequenceNumber();

            if (p.getPayloadJson() != null && !p.getPayloadJson().isEmpty()) {
                p.setPayloadJson(SecurityUtil.decrypt(p.getPayloadJson()));
            }
            return p;
        } catch (Exception e) { return null; }
    }

    private void cleanup() {
        if (session != null) {
            sessionMgr.remove(session.getClientId());
            System.out.println(">> [Server] Thiết bị ID " + session.getClientId() + " đã ngắt kết nối.");
        }
        try { socket.close(); } catch (IOException e) {}
    }
}