package com.lancontrol.server.db;

import java.sql.*;
import java.util.Optional;

public class GroupOnboardingKeyDAO {
    public Optional<Integer> validateKey(String onboardingKey) {
        String sql = "SELECT group_id, token_expiry FROM group_onboarding_keys WHERE group_token = ?";
        Connection conn = null;
        try {
            conn = JDBCUtil.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, onboardingKey);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Timestamp expiry = rs.getTimestamp("token_expiry");
                if (expiry != null && expiry.after(new Timestamp(System.currentTimeMillis()))) {
                    return Optional.of(rs.getInt("group_id"));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); } finally { JDBCUtil.close(conn); }
        return Optional.empty();
    }
}