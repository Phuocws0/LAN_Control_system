package com.lancontrol.server.service;

import com.lancontrol.server.db.ClientIdentityDAO;
import com.lancontrol.server.db.JDBCUtil;
import com.lancontrol.server.db.LogDAO;
import com.lancontrol.server.model.ClientDevice;
import com.lancontrol.server.network.SessionManager;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.sql.*;
import java.util.*;
import com.lancontrol.server.model.ClientDevice;
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
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }
    private String getServerLanIp() {
        // CÁCH 1: KẾT NỐI GIẢ LẬP (Độ chính xác 99%)
        // Tạo một kết nối UDP thử nghiệm đến Google DNS (8.8.8.8)
        // Hệ điều hành sẽ tự động chọn Card mạng chính (Wi-Fi hoặc dây Lan) để định tuyến.
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();

            // Nếu lấy được IP hợp lệ (không phải localhost) thì trả về ngay
            if (ip != null && !ip.startsWith("127.")) {
                System.out.println(">> [IP-Check] Phát hiện IP chính qua định tuyến: " + ip);
                return ip;
            }
        } catch (Exception e) {
            System.err.println(">> [IP-Check] Không thể dò tuyến (Máy không có Internet?), chuyển sang duyệt thủ công.");
        }

        // CÁCH 2: DUYỆT THỦ CÔNG (Fallback khi Cách 1 thất bại)
        // Dùng khi mạng LAN nội bộ hoàn toàn không có Internet
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Bỏ qua Loopback (127.0.0.1) và Card chưa bật
                if (iface.isLoopback() || !iface.isUp()) continue;

                // Bỏ qua các Card ảo thường gặp
                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("docker") || name.contains("vment") || name.contains("virtual") || name.contains("wsl")) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Chỉ lấy IPv4
                    if (addr.isSiteLocalAddress() && !addr.getHostAddress().contains(":")) {
                        String ip = addr.getHostAddress();
                        // Ưu tiên dải 192.168.x.x
                        if (ip.startsWith("192.168.")) return ip;
                        // Hoặc dải 10.x.x.x (Mạng công ty)
                        if (ip.startsWith("10.")) return ip;
                        // Hoặc dải 172.16.x.x -> 172.31.x.x
                        if (ip.startsWith("172.")) return ip;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Cùng đường mới phải trả về localhost
        return "127.0.0.1";
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
    public int insertGroup(String name) {
        String sql = "INSERT INTO groups (group_name) VALUES (?)";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            if (ps.executeUpdate() > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1); // Trả về ID nhóm mới tạo [cite: 254]
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }
    public boolean updateGroupName(int groupId, String newName) {
        String sql = "UPDATE groups SET group_name = ? WHERE group_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setInt(2, groupId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
    public boolean deleteGroup(int groupId) {
        revokeAllInGroup(groupId); // Thu hồi toàn bộ máy trong nhóm trước [cite: 401]
        String sql = "DELETE FROM groups WHERE group_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
    public List<ClientDevice> getClientsByGroup(int groupId) {
        List<ClientDevice> clients = new ArrayList<>();
        String sql = "SELECT * FROM client_devices WHERE group_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ClientDevice d = new ClientDevice();
                d.setClientId(rs.getInt("client_id"));
                d.setClientName(rs.getString("client_name"));
                d.setMacAddress(rs.getString("mac_address"));
                d.setOnline(rs.getBoolean("is_online"));
                clients.add(d);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return clients;
    }
    private void saveKeyToFile(String groupName, String key) {
        String fileName = groupName + ".grpkey";

        // Lấy IP thật của máy Server
        String serverIp = getServerLanIp();

        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write("GROUP_NAME=" + groupName + "\n");

            // --- BẮT BUỘC PHẢI GHI DÒNG NÀY ---
            writer.write("SERVER_IP=" + serverIp + "\n");
            writer.write("SERVER_PORT=9999\n"); // Ghi thêm Port cho chắc chắn
            // ----------------------------------

            writer.write("ONBOARDING_KEY=" + key + "\n");

            System.out.println(">> [Server] Đã xuất file khóa nhóm: " + fileName);
            System.out.println(">> [Server] Nội dung IP ghi trong file: " + serverIp);

        } catch (IOException e) {
            System.err.println(">> Lỗi khi ghi file key: " + e.getMessage());
        }
    }
    public Map<Integer, String> getAllGroups() {
        Map<Integer, String> map = new java.util.HashMap<>();
        String sql = "SELECT group_id, group_name FROM groups";
        try (Connection conn = JDBCUtil.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getInt("group_id"), rs.getString("group_name"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return map;
    }

    public boolean revokeClient(int clientId) {
        String reason = "Thu hồi truy cập theo yêu cầu.";
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
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}