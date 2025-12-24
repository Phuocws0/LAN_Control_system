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

//    @Override
//    public void run() {
//        try {
//            // Khởi tạo luồng nhị phân
//            dos = new DataOutputStream(socket.getOutputStream());
//            dis = new DataInputStream(socket.getInputStream());
//            socket.setSoTimeout(60000);
//
//            // 1. XỬ LÝ XÁC THỰC BAN ĐẦU (Theo giao thức Binary)
//            if (!handleHandshake()) return;
//
//            // 2. VÒNG LẶP NHẬN DỮ LIỆU LIÊN TỤC
//            while (!socket.isClosed()) {
//                // Đọc Type (1 byte)
//                byte type = dis.readByte();
//
//                // Đọc Size (4 bytes)
//                int size = dis.readInt();
//                if (size <= 0 || size > 20 * 1024 * 1024) {
//                    System.err.println(">> [CẢNH BÁO] Client gửi gói tin sai kích thước: " + size + " bytes -> Đóng kết nối ngay!");
//                    throw new IOException("Kích thước gói tin không hợp lệ: " + size);
//
//                }
//
//                // Đọc dữ liệu Payload
//                byte[] payload = new byte[size];
//                dis.readFully(payload);
//
//                if (type == 0x01) {
//                    // Type 0x01: Gói tin JSON (Lệnh, Heartbeat)
//                    String json = new String(payload, StandardCharsets.UTF_8);
//                    handleJsonPacket(json);
//                } else if (type == 0x02) {
//                    // Type 0x02: ẢNH RAW (Không qua Base64/AES - Cực nhanh)
//                    if (session != null) {
//                        commandService.onScreenFrameReceived(session.getClientId(), payload);
//                    }
//                }
//            }
//        } catch (EOFException e) {
//            System.out.println(">> [ClientHandler] Client ngắt kết nối chủ động.");
//        } catch (Exception e) {
//            System.err.println(">> [ClientHandler] Lỗi kết nối: " + e.getMessage());
//        }
//        finally {
//            cleanup();
//        }
//    }


    @Override
    public void run() {
        System.out.println(">> [ClientHandler] Đã khởi động luồng xử lý cho: " + socket.getInetAddress());

        try {
            // Khởi tạo luồng nhị phân
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            socket.setSoTimeout(60000); // 60s không gửi gì thì ngắt

            // 1. XỬ LÝ XÁC THỰC BAN ĐẦU
            if (!handleHandshake()) {
                System.err.println(">> [ClientHandler] Handshake thất bại -> Đóng kết nối.");
                return;
            }

            // 2. VÒNG LẶP NHẬN DỮ LIỆU
            while (socket != null && !socket.isClosed()) {

                // A. Đọc Type (1 byte)
                byte type;
                try {
                    type = dis.readByte();
                } catch (EOFException e) {
                    // Client đóng kết nối bình thường, không cần báo lỗi đỏ
                    System.out.println(">> [INFO] Client ngắt kết nối (EOF).");
                    break;
                }

                // B. Đọc Size (4 bytes)
                int size = dis.readInt();

                // [QUAN TRỌNG] Validate Size chặt chẽ
                if (size <= 0 || size > 20 * 1024 * 1024) { // Max 20MB
                    System.err.println(">> [SECURITY] Size gói tin bất thường: " + size + " bytes -> Ngắt kết nối!");
                    break; // Thoát vòng lặp
                }

                // C. Đọc Payload (Dễ gây tràn RAM nhất)
                byte[] payload = new byte[size];
                dis.readFully(payload);

                // D. Xử lý logic
                if (type == 0x01) {
                    // Type 0x01: JSON
                    String json = new String(payload, StandardCharsets.UTF_8);
                    try {
                        handleJsonPacket(json);
                    } catch (Exception e) {
                        System.err.println(">> [LỖI XỬ LÝ JSON] " + e.getMessage());
                        e.printStackTrace(); // In ra để biết lỗi logic ở đâu
                    }
                }
                else if (type == 0x02) {
                    // Type 0x02: ẢNH RAW
                    if (session != null) {
                        try {
                            // Thêm try-catch riêng cho xử lý ảnh để không làm sập kết nối nếu ảnh lỗi
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
                    // Có thể break hoặc skip tùy logic, nhưng thường là lệch byte nên break luôn
                    // break;
                }
            }

        } catch (IOException e) {
            // Lỗi mạng thông thường
            System.out.println(">> [ClientHandler] Kết nối gián đoạn: " + e.getMessage());
        } catch (Throwable e) {
            // [QUAN TRỌNG] Bắt cả OutOfMemoryError và RuntimeException
            System.err.println(">> [FATAL ERROR] Server gặp lỗi nghiêm trọng!");
            e.printStackTrace();
        } finally {
            System.out.println(">> [ClientHandler] Đang dọn dẹp tài nguyên...");
            cleanup();
        }
    }

    private boolean handleHandshake() {
        System.out.println(">> [HANDSHAKE] Bắt đầu đọc gói tin đầu tiên...");
        try {
            // 1. Đọc Type
            byte type = dis.readByte();
            if (type != 0x01) {
                System.err.println(">> [LỖI] Type gói tin đầu không phải JSON (0x01). Nhận được: " + type);
                return false;
            }

            // 2. Đọc Size
            int size = dis.readInt();
            if (size <= 0 || size > 1024 * 1024) {
                System.err.println(">> [LỖI] Kích thước gói tin không hợp lệ: " + size);
                return false;
            }

            // 3. Đọc Data
            byte[] data = new byte[size];
            dis.readFully(data);
            String jsonRaw = new String(data, StandardCharsets.UTF_8);

            // 4. Giải mã
            // [ĐIỂM CHẾT 1] Nếu sai Key hoặc sai HMAC, hàm này trả về null -> return false
            NetworkPacket p = decryptAndVerify(jsonRaw);
            if (p == null) {
                System.err.println(">> [LỖI] Giải mã thất bại (Sai Token/Key hoặc HMAC).");
                return false;
            }

            System.out.println(">> [HANDSHAKE] Nhận lệnh: " + p.getCommand());

            // 5. Xử lý logic
            if ("ONBOARDING".equals(p.getCommand())) {
                try {
                    OnboardingPayload pl = JsonUtil.fromJson(p.getPayloadJson(), OnboardingPayload.class);

                    // [ĐIỂM CHẾT 2] Chỗ này dễ dính lỗi SQL Duplicate nếu chưa fix xong Database
                    AuthResponse resp = authService.processOnboarding(pl, socket.getInetAddress().getHostAddress());

                    sendSecure("ONBOARDING_RESPONSE", null, resp);
                    System.out.println(">> [INFO] Onboarding thành công -> Đóng kết nối để Client login lại.");

                    return false; // Trả về false để ngắt kết nối là ĐÚNG với logic Onboard (nhưng log bên ngoài sẽ báo thất bại)
                } catch (Exception e) {
                    System.err.println(">> [LỖI ONBOARDING] " + e.getMessage());
                    e.printStackTrace(); // In ra xem có phải lỗi SQL không
                    throw e;
                }
            }
            else if ("AUTH_DAILY".equals(p.getCommand())) {
                // [ĐIỂM CHẾT 3] Tìm không thấy device (Token sai hoặc database chưa có)
                ClientDevice dev = authService.getClientByToken(p.getToken());

                if (dev != null) {
                    // Khởi tạo Session
                    session = new ClientSession(dev.getClientId(), dev.getGroupId(), socket, dos); // Đã sửa null -> dos
                    session.setHostname(dev.getClientName());
                    session.setMacAddress(dev.getMacAddress());
                    session.setIpAddress(socket.getInetAddress().getHostAddress());

                    sessionMgr.add(session);
                    sendSecure("AUTH_SUCCESS", null, "Access Granted");
                    System.out.println(">> [SUCCESS] Thiết bị " + dev.getClientName() + " đã kết nối.");
                    return true; // OK -> Giữ kết nối
                } else {
                    System.err.println(">> [LỖI] Token không tồn tại hoặc sai: " + p.getToken());
                    sendSecure("AUTH_FAILED", null, "Invalid Token");
                    return false;
                }
            } else {
                System.err.println(">> [LỖI] Lệnh lạ trong Handshake: " + p.getCommand());
                return false;
            }

        } catch (Exception e) {
            System.err.println(">> [CRITICAL] Lỗi trong quá trình Handshake: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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