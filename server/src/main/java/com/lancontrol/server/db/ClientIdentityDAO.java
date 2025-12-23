package com.lancontrol.server.db;

import com.lancontrol.server.model.ClientDevice;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Optional;
import java.util.UUID;

public class ClientIdentityDAO {

    public String generateNewIdentity(int clientId) {
        String rawToken = UUID.randomUUID().toString();
        String hash = hashToken(rawToken);
        if (hash == null) return null;
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE client_identities SET status = 'revoked' WHERE client_id = ? AND status = 'active'")) {
            ps.setInt(1, clientId);
            ps.executeUpdate();
        } catch(Exception e) {}

        String sql = "INSERT INTO client_identities (client_id, auth_key_hash, key_type, status, expires_at) VALUES (?, ?, 'JWT', 'active', DATE_ADD(NOW(), INTERVAL 30 DAY))";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            ps.setString(2, hash);
            if (ps.executeUpdate() > 0) return rawToken;
        } catch(Exception e) { e.printStackTrace(); }
        return null;
    }

    public Optional<ClientDevice> validateToken(String rawToken) {
        String hash = hashToken(rawToken);
        if (hash == null) return Optional.empty();
        String sql = "SELECT d.* FROM client_identities i JOIN client_devices d ON i.client_id = d.client_id WHERE i.auth_key_hash = ? AND i.status = 'active' AND i.expires_at > NOW()";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, hash);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ClientDevice d = new ClientDevice();
                d.setClientId(rs.getInt("client_id"));
                d.setClientName(rs.getString("client_name"));
                d.setMacAddress(rs.getString("mac_address"));
                d.setCurrentIp(rs.getString("current_ip"));
                return Optional.of(d);
            }
        } catch(Exception e) { e.printStackTrace(); }
        return Optional.empty();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) { return null; }
    }
}