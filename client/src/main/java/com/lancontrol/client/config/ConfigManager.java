package com.lancontrol.client.config;

import java.io.*;
import java.util.Properties;

public class ConfigManager {
    private static final String FILE = "client.properties";
    private Properties p = new Properties();

    public ConfigManager() {
        loadConfig();
        // Nếu chưa có Token hoặc IP server chưa được set đúng (còn default),
        // thì cố gắng tìm file .grpkey để nạp lại
        if (getToken() == null || isDefaultIp()) {
            autoLoadGroupKey();
        }
    }

    private void loadConfig() {
        try {
            File f = new File(FILE);
            if (f.exists()) {
                try (FileInputStream fis = new FileInputStream(f)) { p.load(fis); }
            }
        } catch (Exception e) {}
    }

    private void autoLoadGroupKey() {
        File folder = new File(".");
        // Tìm tất cả các file có đuôi .grpkey
        File[] grpFiles = folder.listFiles((dir, name) -> name.endsWith(".grpkey"));

        if (grpFiles != null && grpFiles.length > 0) {
            Properties grpProps = new Properties();
            try (FileInputStream fis = new FileInputStream(grpFiles[0])) {
                grpProps.load(fis);

                String key = grpProps.getProperty("ONBOARDING_KEY");
                String ip = grpProps.getProperty("SERVER_IP");

                if (key != null && ip != null) {
                    p.setProperty("key", key);
                    p.setProperty("server_ip", ip); // Lưu IP Server vào config

                    // Lưu ngay lập tức vào file client.properties để lần sau không cần file key nữa
                    saveConfig();
                    System.out.println(">> [Client] Đã nạp cấu hình từ file: " + grpFiles[0].getName());
                    System.out.println(">> [Client] Server IP: " + ip);
                }
            } catch (Exception e) {
                System.err.println(">> Lỗi nạp file .grpkey: " + e.getMessage());
            }
        }
    }
    public boolean importKeyFile(File keyFile) {
        if (keyFile == null || !keyFile.exists()) return false;

        Properties grpProps = new Properties();
        try (FileInputStream fis = new FileInputStream(keyFile)) {
            grpProps.load(fis);

            String key = grpProps.getProperty("ONBOARDING_KEY");
            String ip = grpProps.getProperty("SERVER_IP");
            String port = grpProps.getProperty("SERVER_PORT"); // Đọc thêm Port

            if (key != null && !key.isEmpty()) {
                p.setProperty("key", key);

                // Nếu file có IP thì lấy, không thì mặc định localhost
                if (ip != null && !ip.isEmpty()) {
                    p.setProperty("server_ip", ip);
                } else {
                    p.setProperty("server_ip", "127.0.0.1");
                }

                // Lưu Port nếu có (dự phòng tương lai)
                if (port != null) {
                    p.setProperty("server_port", port);
                }

                saveConfig();
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean isDefaultIp() {
        String ip = p.getProperty("server_ip");
        return ip == null || ip.isEmpty() || ip.equals("127.0.0.1") || ip.equals("localhost");
    }

    public void saveToken(String t) {
        if (t == null) {
            // Nếu truyền vào null thì XÓA luôn key 'token' khỏi file
            p.remove("token");
        } else {
            // Nếu có giá trị thì lưu bình thường
            p.setProperty("token", t);
        }
        saveConfig();
    }

    private void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(FILE)) {
            p.store(fos, "LanControl Client Config");
        } catch (Exception e) {}
    }

    public String getToken() { return p.getProperty("token"); }
    public String getKey() { return p.getProperty("key"); }

    // Hàm này được SocketClient gọi
    public String getServerIp() {
        return p.getProperty("server_ip", "127.0.0.1");
    }
    public boolean isConfigured() {
        return (getToken() != null && !getToken().isEmpty()) ||
                (getKey() != null && !getKey().isEmpty());
    }
}