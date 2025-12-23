package com.lancontrol.server.service;

import com.lancontrol.server.db.ClientIdentityDAO;
import com.lancontrol.server.db.JDBCUtil;
import com.lancontrol.server.db.LogDAO;
import com.lancontrol.server.network.SessionManager;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.List;
import java.util.UUID;

public class GroupService {
    private final ClientIdentityDAO identityDAO = new ClientIdentityDAO();
    private final LogDAO logDAO = new LogDAO();
    private final CommandService commandService;
    private final SessionManager sessionMgr;

    public GroupService(CommandService commandService, SessionManager sessionMgr) {
        this.commandService = commandService;
        this.sessionMgr = sessionMgr;
    }
    public String createGroupKey(int groupId, String groupName, int daysValid) {
        //tao key dang 16 ky tu
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String sql = "INSERT INTO group_onboarding_keys (group_id, group_token, token_expiry) " +
                "VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? DAY))";

        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setString(2, key);
            ps.setInt(3, daysValid);

            if (ps.executeUpdate() > 0) {
                saveKeyToFile(groupName, key);
                return key;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void saveKeyToFile(String groupName, String key) {
        String fileName = groupName + ".grpkey";
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write("GROUP_NAME=" + groupName + "\n");
            writer.write("ONBOARDING_KEY=" + key + "\n");
            System.out.println(">> [Server] Đã xuất file khóa nhóm: " + fileName);
        } catch (IOException e) {
            System.err.println(">> Lỗi khi ghi file key: " + e.getMessage());
        }
    }

   //doi nhom
    public boolean migrateClient(int clientId, int targetGroupId) {
        Connection conn = null;
        try {
            conn = JDBCUtil.getConnection();
            conn.setAutoCommit(false);
            String updateDev = "UPDATE client_devices SET group_id = ? WHERE client_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateDev)) {
                ps.setInt(1, targetGroupId);
                ps.setInt(2, clientId);
                ps.executeUpdate();
            }
            //thu hoi token cu
            String newToken = identityDAO.generateNewIdentity(clientId);
            if (newToken != null) {
                conn.commit();
                //gui lenh migrate
                commandService.sendMigrate(clientId, newToken);
                logDAO.insertLog(clientId, "MIGRATE", "Đã chuyển thiết bị sang nhóm ID: " + targetGroupId);
                return true;
            }
            conn.rollback();
        } catch (SQLException e) {
            try { if(conn != null) conn.rollback(); } catch (Exception ex) {}
            e.printStackTrace();
        } finally { JDBCUtil.close(conn); }
        return false;
    }
    //thu hoi 1 thiet bi
    public void revokeClient(int clientId, String reason) {
        String sql = "UPDATE client_identities SET status = 'revoked', revocation_reason = ? " +
                "WHERE client_id = ? AND status = 'active'";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setInt(2, clientId);

            if (ps.executeUpdate() > 0) {
                // ngat ket noi neu dang online
                commandService.sendRevoke(clientId);
                logDAO.insertLog(clientId, "REVOKE", "Thu hồi truy cập. Lý do: " + reason);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    //thu hoi nhieu thiet bi trong 1 nhom
    public void revokeAllInGroup(int groupId) {
        String sql = "UPDATE client_identities i " +
                "JOIN client_devices d ON i.client_id = d.client_id " +
                "SET i.status = 'revoked', i.revocation_reason = 'Group deleted or bulk revoked' " +
                "WHERE d.group_id = ? AND i.status = 'active'";

        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.executeUpdate();
            List<Integer> onlineClients = sessionMgr.getClientIdsByGroup(groupId);
            for (int cid : onlineClients) {
                commandService.sendRevoke(cid);
                logDAO.insertLog(cid, "BULK_REVOKE", "Thu hồi hàng loạt do thao tác nhóm.");
            }
            System.out.println(">> [Server] Đã thu hồi toàn bộ thiết bị thuộc nhóm ID: " + groupId);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}