package com.lancontrol.server.service;

import com.lancontrol.server.db.*;
import com.lancontrol.server.model.*;
import java.util.Optional;

public class AuthService {
    private final GroupOnboardingKeyDAO keyDAO = new GroupOnboardingKeyDAO();
    private final ClientDeviceDAO deviceDAO = new ClientDeviceDAO();
    private final ClientIdentityDAO identityDAO = new ClientIdentityDAO();
    private final LogDAO logDAO = new LogDAO();

    public AuthResponse processOnboarding(OnboardingPayload payload, String socketIp) {
        // 1. Kiểm tra Key kích hoạt nhóm
        Optional<Integer> gid = keyDAO.validateKey(payload.getOnboardingKey());

        if (gid.isEmpty()) {
            logDAO.insertLog(null, "AUTH_FAILURE", "Invalid onboarding key used from IP: " + socketIp);
            return new AuthResponse(false, "INVALID_KEY", null);
        }

        // --- BẮT ĐẦU LOGIC SỬA IP (QUAN TRỌNG) ---
        // Mục đích: Xác định IP nào là đúng để lưu vào Database.

        if (socketIp != null && !socketIp.startsWith("127.") && !socketIp.equals("0:0:0:0:0:0:0:1")) {
            // TRƯỜNG HỢP 1: Kết nối từ máy khác trong mạng LAN (IP thật, VD: 192.168.1.50).
            // -> Ta tin tưởng tuyệt đối vào IP của Socket này.
            // -> Ghi đè IP này vào payload để lưu xuống DB.
            System.out.println(">> [AuthService] Phát hiện kết nối LAN thực. Ghi đè IP Client: " + socketIp);
            payload.setCurrentIp(socketIp);
        } else {
            // TRƯỜNG HỢP 2: Kết nối là Localhost (127.0.0.1).
            // -> Có thể Server và Client đang chạy chung 1 máy.
            // -> Lúc này IP Socket (127.0.0.1) là vô nghĩa với việc quản lý.
            // -> Ta sẽ tin tưởng vào IP mà Client đã tự tìm được (qua HardwareUtil) và gửi lên trong payload.

            if (payload.getCurrentIp() == null || payload.getCurrentIp().isEmpty()) {
                payload.setCurrentIp(socketIp); // Fallback nếu client quên gửi IP
            }
            System.out.println(">> [AuthService] Kết nối Localhost. Giữ nguyên IP Client báo cáo: " + payload.getCurrentIp());
        }
        // --- KẾT THÚC LOGIC SỬA IP ---

        // 2. Kiểm tra thiết bị đã tồn tại chưa
        int cid = -1;
        Optional<ClientDevice> existing = deviceDAO.getClientByMac(payload.getMacAddress());

        if (existing.isPresent()) {
            cid = existing.get().getClientId();

            // (Tùy chọn) Cập nhật lại Group ID và IP cho thiết bị cũ nếu họ đăng ký lại
            // deviceDAO.updateDeviceGroupAndIp(cid, gid.get(), payload.getCurrentIp());
        } else {
            // 3. Nếu chưa có thì tạo mới (Lúc này current_ip trong payload đã chuẩn)
            cid = deviceDAO.insertNewDevice(payload, gid.get());
        }

        if (cid == -1) {
            return new AuthResponse(false, "DB_ERROR", null);
        }

        // 4. Sinh Token xác thực mới
        String token = identityDAO.generateNewIdentity(cid);

        // 5. Ghi log thành công
        logDAO.insertLog(cid, "ONBOARDING", "Thiết bị đã đăng ký thành công vào nhóm ID: " + gid.get() + " (IP: " + payload.getCurrentIp() + ")");

        return new AuthResponse(true, "SUCCESS", token);
    }

    public ClientDevice getClientByToken(String token) {
        Optional<ClientDevice> device = identityDAO.validateToken(token);
        return device.orElse(null);
    }
}