package com.lancontrol.server.db;

import com.lancontrol.server.model.Group;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {
    public List<Group> getAllGroups() {
        List<Group> list = new ArrayList<>();
        String sql = "SELECT * FROM groups";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Group g = new Group();
                g.setGroupId(rs.getInt("group_id"));
                g.setGroupName(rs.getString("group_name"));
                g.setCreatedAt(rs.getTimestamp("created_at"));
                list.add(g);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public int insertGroup(String name) {
        String sql = "INSERT INTO groups (group_name) VALUES (?)";
        try (Connection conn = JDBCUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            if (ps.executeUpdate() > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return -1;
    }
}