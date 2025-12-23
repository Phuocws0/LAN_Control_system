package com.lancontrol.server.db;

import java.sql.Connection;
import java.sql.PreparedStatement;

public class LogDAO {
    public void insertLog(Integer clientId, String type, String message) {
        String sql = "INSERT INTO logs (client_id, log_type, log_message) VALUES (?, ?, ?)";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (clientId != null) ps.setInt(1, clientId);
            else ps.setNull(1, java.sql.Types.INTEGER);

            ps.setString(2, type);
            ps.setString(3, message);
            ps.executeUpdate(); // [cite: 12, 60]
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}