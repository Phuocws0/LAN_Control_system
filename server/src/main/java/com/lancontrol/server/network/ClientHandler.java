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
        System.out.println(">> [ClientHandler] Đã khởi động luồng xử lý cho: " + socket.getInetAddress());

        try {
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            socket.setSoTimeout(60000); // Timeout 60 giay

            if (!handleHandshake()) {
                System.err.println(">> [ClientHandler] Handshake thất bại -> Đóng kết nối.");
                return;
            }
            while (socket != null && !socket.isClosed()) {
                byte type;
                try {
                    type = dis.readByte();
                } catch (EOFException e) {
                    System.out.println(">> [INFO] Client ngắt kết nối (EOF).");
                    break;
                }

                int size = dis.readInt();

                if (size <= 0 || size > 20 * 1024 * 1024) { // Max 20MB
                    System.err.println(">> [SECURITY] Size gói tin bất thường: " + size + " bytes -> Ngắt kết nối!");
                    break;
                }

                byte[] payload = new byte[size];
                dis.readFully(payload);

                if (type == 0x01) {
                    // Type 0x01: JSON
                    String json = new String(payload, StandardCharsets.UTF_8);
                    try {
                        handleJsonPacket(json);
                    } catch (Exception e) {
                        System.err.println(">> [LỖI XỬ LÝ JSON] " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                else if (type == 0x02) {
                    if (session != null) {
                        try {
                            commandService.onScreenFrameReceived(session.getClientId(), payload);
                        } catch (Exception e) {
                            System.err.println(">> [LỖI XỬ LÝ ẢNH] " + e.getMessage());
                        }
                    } else {
                        System.out.println(">> [WARN] Nhận ảnh khi chưa có Session.");
                    }
                }
                else {
                    System.out.println(">> [WARN] Loại gói tin không hỗ trợ: " + type);
                }
            }

        } catch (IOException e) {
            System.out.println(">> [ClientHandler] Kết nối gián đoạn: " + e.getMessage());
        } catch (Throwable e) {
            System.err.println(">> [FATAL ERROR] Server gặp lỗi nghiêm trọng!");
            e.printStackTrace();
        } finally {
            System.out.println(">> [ClientHandler] Đang dọn dẹp tài nguyên...");
            cleanup();
        }
    }
    // xu ly qua trinh handshake
    private boolean handleHandshake() {
        System.out.println(">> [HANDSHAKE] Bắt đầu đọc gói tin đầu tiên...");
        try {
            // doc loai goi tin
            byte type = dis.readByte();
            if (type != 0x01) {
                System.err.println(">> [LỖI] Gói đầu tiên không phải JSON (0x01). Nhận được: " + type);
                return false;
            }
            // doc kich thuoc goi tin
            int size = dis.readInt();
            if (size <= 0 || size > 2 * 1024 * 1024) {
                System.err.println(">> [LỖI] Kích thước gói tin không hợp lệ: " + size);
                return false;
            }
            // doc du lieu goi tin
            byte[] data = new byte[size];
            dis.readFully(data);
            String jsonRaw = new String(data, StandardCharsets.UTF_8);

            NetworkPacket p = decryptAndVerify(jsonRaw);

            if (p == null) {
                System.err.println(">> [LỖI] Giải mã thất bại (Sai Token/Key hoặc HMAC). IP: " + socket.getInetAddress());
                return false;
            }

            System.out.println(">> [HANDSHAKE] Nhận lệnh: " + p.getCommand());


            // truong hop 1: onboarding
            if ("ONBOARDING".equals(p.getCommand())) {
                try {
                    OnboardingPayload pl = JsonUtil.fromJson(p.getPayloadJson(), OnboardingPayload.class);
                    if (pl == null) return false;
                    // Xu ly onboarding
                    String socketIp = socket.getInetAddress().getHostAddress();
                    AuthResponse resp = authService.processOnboarding(pl, socketIp);
                    // Gui phan hoi ve Client
                    sendSecure("ONBOARDING_RESPONSE", null, resp);
                    // In thong bao ket qua
                    if (resp.isSuccess()) {
                        System.out.println(">> [INFO] Onboarding thành công cho thiết bị: " + pl.getClientName());
                        System.out.println(">> [INFO] Đóng kết nối để Client tự động Login lại bằng Token mới.");
                    } else {
                        System.err.println(">> [WARN] Onboarding thất bại: " + resp.getMessage());
                    }
                    // Dong ket noi sau khi onboarding
                    return false;
                } catch (Exception e) {
                    System.err.println(">> [LỖI ONBOARDING] " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }

            // truong hop 2: xac thuc hang ngay
            else if ("AUTH_DAILY".equals(p.getCommand())) {
                ClientDevice dev = authService.getClientByToken(p.getToken());
                if (dev != null) {
                    String socketIp = socket.getInetAddress().getHostAddress();
                    if (!socketIp.startsWith("127.") &&
                            !socketIp.equals("0:0:0:0:0:0:0:1") &&
                            !socketIp.equals(dev.getCurrentIp())) {
                        // cap nhat vao database va ram
                        ClientDeviceDAO dao = new ClientDeviceDAO();
                        dao.updateIp(dev.getClientId(), socketIp); // Đảm bảo DAO có hàm updateIp
                        // cap nhat lai trong doi tuong
                        dev.setCurrentIp(socketIp);
                        System.out.println(">> [Info] Đã cập nhật IP mới cho " + dev.getClientName() + ": " + socketIp);
                    }
                    //khoi tao session moi
                    session = new ClientSession(dev.getClientId(), dev.getGroupId(), socket, dos);
                    session.setHostname(dev.getClientName());
                    session.setMacAddress(dev.getMacAddress());
                    session.setIpAddress(dev.getCurrentIp());
                    // dang ky session
                    sessionMgr.add(session);
                    // gui phan hoi thanh cong
                    sendSecure("AUTH_SUCCESS", null, "Access Granted");
                    System.out.println(">> [SUCCESS] Thiết bị " + dev.getClientName() + " (ID: " + dev.getClientId() + ") đã kết nối.");

                    return true; // Hoan thanh Handshake
                } else {
                    System.err.println(">> [LỖI] Token không tồn tại hoặc đã bị thu hồi: " + p.getToken());
                    sendSecure("AUTH_FAILED", null, "Invalid Token");
                    return false;
                }
            }

            // truong hop 3: lenh la
            else {
                System.err.println(">> [LỖI] Lệnh lạ trong quá trình Handshake: " + p.getCommand());
                return false;
            }

        } catch (Exception e) {
            System.err.println(">> [CRITICAL] Lỗi ngoại lệ trong Handshake: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    // xu ly goi tin dang json
    private void handleJsonPacket(String json) {
        NetworkPacket pkt = decryptAndVerify(json);
        if (pkt == null) return;

        if ("HEARTBEAT".equals(pkt.getCommand())) {
            HeartbeatModel hb = JsonUtil.fromJson(pkt.getPayloadJson(), HeartbeatModel.class);
            hbService.processHeartbeat(session.getClientId(), hb);
        } else {
            commandService.handleClientResponse(session.getClientId(), pkt);
        }
    }

    // gui goi tin bao mat den client
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
    // giai ma va xac thuc goi tin nhan duoc
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