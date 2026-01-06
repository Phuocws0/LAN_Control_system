package com.lancontrol.server.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class JDBCUtil {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/lan_control_db";
    private static final String USER = "root";
    private static final String PASS = "";

    public static Connection getConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USER);
        props.setProperty("password", PASS);
        props.setProperty("autoReconnect", "true");
        props.setProperty("characterEncoding", "UTF-8");

        try {
            return DriverManager.getConnection(DB_URL, props);
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối CSDL: " + e.getMessage());
            throw e;
        }
    }

    public static void close(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("Lỗi đóng Connection: " + e.getMessage());
            }
        }
    }
}