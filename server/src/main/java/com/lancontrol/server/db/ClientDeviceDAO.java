package com.lancontrol.server.db;

import com.lancontrol.server.model.ClientDevice;
import com.lancontrol.server.model.HeartbeatModel;
import com.lancontrol.server.model.OnboardingPayload;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClientDeviceDAO {

    public Optional<ClientDevice> getClientByMac(String macAddress) {
        String sql = "SELECT * FROM client_devices WHERE mac_address = ?";
        Connection conn = null;
        try {
            conn = JDBCUtil.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, macAddress);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ClientDevice d = new ClientDevice();
                d.setClientId(rs.getInt("client_id"));
                d.setMacAddress(rs.getString("mac_address"));
                d.setClientName(rs.getString("client_name"));
                return Optional.of(d);
            }
        } catch (SQLException e) { e.printStackTrace(); } finally { JDBCUtil.close(conn); }
        return Optional.empty();
    }

    public int insertNewDevice(OnboardingPayload p, int groupId) {
        String sql = "INSERT INTO client_devices (mac_address, group_id, client_name, current_ip, os, cpu_info, ram_total, disk_total_gb) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        int id = -1;
        try {
            conn = JDBCUtil.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, p.getMacAddress());
            ps.setInt(2, groupId);
            ps.setString(3, p.getClientName());
            ps.setString(4, p.getCurrentIp());
            ps.setString(5, p.getOs());
            ps.setString(6, p.getCpuInfo());
            ps.setLong(7, p.getRamTotal());
            ps.setInt( 8, p.getDiskTotalGb());

            if (ps.executeUpdate() > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) id = rs.getInt(1);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); } finally { JDBCUtil.close(conn); }
        return id;
    }

    public boolean updateHeartbeat(int clientId, HeartbeatModel hb) {
        String update = "UPDATE client_devices SET last_seen = NOW(), is_online = TRUE WHERE client_id = ?";
        String insert = "INSERT INTO client_heartbeat (client_id, cpu_usage, ram_usage, disk_write_rate_kb, disk_read_rate_kb, disk_free_percent, net_sent_rate_kb, net_recv_rate_kb) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = JDBCUtil.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setInt(1, clientId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setInt(1, clientId);
                ps.setShort(2, hb.getCpuUsage());
                ps.setShort(3, hb.getRamUsage());
                ps.setInt(4, hb.getDiskWriteRateKb());
                ps.setInt(5, hb.getDiskReadRateKb());
                ps.setShort(6, hb.getDiskFreePercent());
                ps.setInt(7, hb.getNetSentRateKb());
                ps.setInt(8, hb.getNetRecvRateKb());
                ps.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            try { if(conn!=null) conn.rollback(); } catch(Exception ex){}
            return false;
        } finally { JDBCUtil.close(conn); }
    }
    public Optional<ClientDevice> getClientById(int clientId) {
        String sql = "SELECT * FROM client_devices WHERE client_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, clientId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                ClientDevice d = new ClientDevice();
                d.setClientId(rs.getInt("client_id"));
                d.setGroupId(rs.getInt("group_id"));
                d.setMacAddress(rs.getString("mac_address"));
                d.setClientName(rs.getString("client_name"));
                d.setCurrentIp(rs.getString("current_ip"));
                d.setOnline(rs.getBoolean("is_online"));
                d.setOs(rs.getString("os"));
                d.setCpuInfo(rs.getString("cpu_info"));
                d.setRamTotal(rs.getLong("ram_total"));
                d.setDiskTotalGb(rs.getInt("disk_total_gb"));
                return Optional.of(d);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public void updateCurrentIp(int clientId, String socketIp) {
        String sql = "UPDATE client_devices SET current_ip = ? WHERE client_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, socketIp);
            ps.setInt(2, clientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateIp(int clientId, String socketIp) {
        String sql = "UPDATE client_devices SET current_ip = ? WHERE client_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, socketIp);
            ps.setInt(2, clientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public List<ClientDevice> getAllClients() {
        List<ClientDevice> list = new ArrayList<>();
        String sql = "SELECT * FROM client_devices";
        try (Connection conn = JDBCUtil.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToDevice(rs));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    public List<ClientDevice> getClientsByGroup(int groupId) {
        List<ClientDevice> list = new ArrayList<>();
        String sql = "SELECT * FROM client_devices WHERE group_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapResultSetToDevice(rs));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    private ClientDevice mapResultSetToDevice(ResultSet rs) throws SQLException {
        ClientDevice d = new ClientDevice();
        d.setClientId(rs.getInt("client_id"));
        d.setGroupId(rs.getInt("group_id"));
        d.setClientName(rs.getString("client_name"));
        d.setCurrentIp(rs.getString("current_ip"));
        d.setMacAddress(rs.getString("mac_address"));
        d.setOnline(rs.getBoolean("is_online"));

        // --- LẤY THÊM THÔNG TIN CẤU HÌNH ---
        // (Đảm bảo tên cột khớp với Database của bạn)
        d.setOs(rs.getString("os"));
        d.setCpuInfo(rs.getString("cpu_info"));
        d.setRamTotal(rs.getLong("ram_total"));
        d.setDiskTotalGb(rs.getInt("disk_total_gb"));
        // ------------------------------------

        return d;
    }
    public boolean deleteDevice(int clientId) {
        String sql = "DELETE FROM client_devices WHERE client_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, clientId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
    public List<Integer> getClientIdsByGroup(int groupId) {
        List<Integer> list = new ArrayList<>();
        String sql = "SELECT client_id FROM client_devices WHERE group_id = ?";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getInt(1));
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

}