package com.lancontrol.server.service;

import com.lancontrol.server.db.*;
import com.lancontrol.server.model.*;
import java.util.Optional;

public class AuthService {
    private final GroupOnboardingKeyDAO keyDAO = new GroupOnboardingKeyDAO();
    private final ClientDeviceDAO deviceDAO = new ClientDeviceDAO();
    private final ClientIdentityDAO identityDAO = new ClientIdentityDAO();
    private final LogDAO logDAO = new LogDAO();

    public AuthResponse processOnboarding(OnboardingPayload payload, String clientIp) {
        Optional<Integer> gid = keyDAO.validateKey(payload.getOnboardingKey());
        if (gid.isEmpty()) {
            logDAO.insertLog(null, "AUTH_FAILURE", "Invalid onboarding key used from IP: " + clientIp);
            return new AuthResponse(false, "INVALID_KEY", null);
        }

        int cid = -1;
        Optional<ClientDevice> existing = deviceDAO.getClientByMac(payload.getMacAddress());
        if (existing.isPresent()) {
            cid = existing.get().getClientId();
        } else {
            cid = deviceDAO.insertNewDevice(payload, gid.get());
        }

        if (cid == -1) return new AuthResponse(false, "DB_ERROR", null);

        String token = identityDAO.generateNewIdentity(cid);
        logDAO.insertLog(cid, "ONBOARDING", "Thiết bị đã đăng ký thành công vào nhóm " + gid.get());
        return new AuthResponse(true, "SUCCESS", token);
    }

    public ClientDevice getClientByToken(String token) {
        return identityDAO.validateToken(token).orElse(null);
    }
}