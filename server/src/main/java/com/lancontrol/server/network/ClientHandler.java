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
            // 1. Đọc Type (1 byte)
            byte type = dis.readByte();
            if (type != 0x01) { // 0x01 là gói JSON
                System.err.println(">> [LỖI] Gói đầu tiên không phải JSON (0x01). Nhận được: " + type);
                return false;
            }

            // 2. Đọc Size (4 byte)
            int size = dis.readInt();
            if (size <= 0 || size > 2 * 1024 * 1024) { // Max 2MB cho gói handshake
                System.err.println(">> [LỖI] Kích thước gói tin không hợp lệ: " + size);
                return false;
            }

            // 3. Đọc Data (Payload đã mã hóa)
            byte[] data = new byte[size];
            dis.readFully(data);
            String jsonRaw = new String(data, StandardCharsets.UTF_8);

            // 4. Giải mã & Kiểm tra HMAC
            // Hàm này sẽ return null nếu sai Key hoặc sai Chữ ký HMAC
            NetworkPacket p = decryptAndVerify(jsonRaw);

            if (p == null) {
                System.err.println(">> [LỖI] Giải mã thất bại (Sai Token/Key hoặc HMAC). IP: " + socket.getInetAddress());
                return false;
            }

            System.out.println(">> [HANDSHAKE] Nhận lệnh: " + p.getCommand());

            // 5. Xử lý Logic từng lệnh

            // --- TRƯỜNG HỢP 1: ĐĂNG KÝ MỚI (ONBOARDING) ---
            if ("ONBOARDING".equals(p.getCommand())) {
                try {
                    OnboardingPayload pl = JsonUtil.fromJson(p.getPayloadJson(), OnboardingPayload.class);
                    if (pl == null) return false;

                    // Gọi AuthService xử lý (Truyền IP socket để ưu tiên IP thật)
                    String socketIp = socket.getInetAddress().getHostAddress();
                    AuthResponse resp = authService.processOnboarding(pl, socketIp);

                    // Gửi phản hồi về Client
                    sendSecure("ONBOARDING_RESPONSE", null, resp);

                    if (resp.isSuccess()) {
                        System.out.println(">> [INFO] Onboarding thành công cho thiết bị: " + pl.getClientName());
                        System.out.println(">> [INFO] Đóng kết nối để Client tự động Login lại bằng Token mới.");
                    } else {
                        System.err.println(">> [WARN] Onboarding thất bại: " + resp.getMessage());
                    }

                    // QUAN TRỌNG: Trả về false để ngắt kết nối socket hiện tại.
                    // Client sẽ nhận Token, lưu vào file và tự kết nối lại bằng lệnh AUTH_DAILY.
                    return false;
                } catch (Exception e) {
                    System.err.println(">> [LỖI ONBOARDING] " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }

            // --- TRƯỜNG HỢP 2: ĐĂNG NHẬP HÀNG NGÀY (AUTH_DAILY) ---
            else if ("AUTH_DAILY".equals(p.getCommand())) {
                // Kiểm tra Token trong Database
                ClientDevice dev = authService.getClientByToken(p.getToken());

                if (dev != null) {
                    // --- CẬP NHẬT IP THÔNG MINH ---
                    String socketIp = socket.getInetAddress().getHostAddress();

                    // Logic: Chỉ update nếu là IP thật (khác localhost/IPv6 loopback) và khác IP trong DB
                    if (!socketIp.startsWith("127.") &&
                            !socketIp.equals("0:0:0:0:0:0:0:1") &&
                            !socketIp.equals(dev.getCurrentIp())) {

                        // 1. Cập nhật vào Database
                        ClientDeviceDAO dao = new ClientDeviceDAO();
                        dao.updateIp(dev.getClientId(), socketIp); // Đảm bảo DAO có hàm updateIp

                        // 2. Cập nhật vào Object trong RAM (để UI Server hiển thị đúng ngay lập tức)
                        dev.setCurrentIp(socketIp);

                        System.out.println(">> [Info] Đã cập nhật IP mới cho " + dev.getClientName() + ": " + socketIp);
                    }
                    // -----------------------------

                    // Khởi tạo Session
                    session = new ClientSession(dev.getClientId(), dev.getGroupId(), socket, dos);
                    session.setHostname(dev.getClientName());
                    session.setMacAddress(dev.getMacAddress());
                    session.setIpAddress(dev.getCurrentIp()); // Lấy IP chuẩn từ Object dev

                    // Thêm vào quản lý session
                    sessionMgr.add(session);

                    // Gửi thông báo thành công
                    sendSecure("AUTH_SUCCESS", null, "Access Granted");
                    System.out.println(">> [SUCCESS] Thiết bị " + dev.getClientName() + " (ID: " + dev.getClientId() + ") đã kết nối.");

                    return true; // Trả về true để giữ kết nối và chuyển sang vòng lặp lắng nghe lệnh
                } else {
                    System.err.println(">> [LỖI] Token không tồn tại hoặc đã bị thu hồi: " + p.getToken());
                    sendSecure("AUTH_FAILED", null, "Invalid Token");
                    return false;
                }
            }

            // --- TRƯỜNG HỢP KHÁC: LỆNH KHÔNG HỢP LỆ ---
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