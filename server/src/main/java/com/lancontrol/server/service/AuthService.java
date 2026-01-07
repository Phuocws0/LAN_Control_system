package com.lancontrol.server.service;

import com.lancontrol.server.db.*;
import com.lancontrol.server.model.*;
import java.util.Optional;

public class AuthService {
    private final GroupOnboardingKeyDAO keyDAO = new GroupOnboardingKeyDAO();
    private final ClientDeviceDAO deviceDAO = new ClientDeviceDAO();
    private final ClientIdentityDAO identityDAO = new ClientIdentityDAO();
    private final LogDAO logDAO = new LogDAO();
    // xu ly onboarding
    public AuthResponse processOnboarding(OnboardingPayload payload, String socketIp) {
        Optional<Integer> gid = keyDAO.validateKey(payload.getOnboardingKey());
        // neu key khong hop le
        if (gid.isEmpty()) {
            logDAO.insertLog(null, "AUTH_FAILURE", "Invalid onboarding key used from IP: " + socketIp);
            return new AuthResponse(false, "INVALID_KEY", null);
        }
        if (socketIp != null && !socketIp.startsWith("127.") && !socketIp.equals("0:0:0:0:0:0:0:1")) {
            System.out.println(">> [AuthService] Phát hiện kết nối LAN thực. Ghi đè IP Client: " + socketIp);
            payload.setCurrentIp(socketIp);
        } else {
            if (payload.getCurrentIp() == null || payload.getCurrentIp().isEmpty()) {
                payload.setCurrentIp(socketIp); // Fallback nếu client quên gửi IP
            }
            System.out.println(">> [AuthService] Kết nối Localhost. Giữ nguyên IP Client báo cáo: " + payload.getCurrentIp());
        }
        // kiem tra thiet bi da ton tai
        int cid = -1;
        Optional<ClientDevice> existing = deviceDAO.getClientByMac(payload.getMacAddress());

        if (existing.isPresent()) {
            cid = existing.get().getClientId();
             deviceDAO.updateDeviceGroupAndIp(cid, gid.get(), payload.getCurrentIp());
        } else {
            cid = deviceDAO.insertNewDevice(payload, gid.get());
        }

        if (cid == -1) {
            return new AuthResponse(false, "DB_ERROR", null);
        }
        String token = identityDAO.generateNewIdentity(cid);
        logDAO.insertLog(cid, "ONBOARDING", "Thiết bị đã đăng ký thành công vào nhóm ID: " + gid.get() + " (IP: " + payload.getCurrentIp() + ")");

        return new AuthResponse(true, "SUCCESS", token);
    }

    public ClientDevice getClientByToken(String token) {
        Optional<ClientDevice> device = identityDAO.validateToken(token);
        return device.orElse(null);
    }
}