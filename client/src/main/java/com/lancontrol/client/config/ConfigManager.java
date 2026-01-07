package com.lancontrol.client.config;

import java.io.*;
import java.util.Properties;

public class ConfigManager {
    private static final String FILE = "client.properties";
    private Properties p = new Properties();
    private static final String DEFAULT_IP = "127.0.0.1";

    // khoi tao va nap cau hinh
    public ConfigManager() {
        loadConfig();
        if (getToken() == null || isDefaultIp()) {
            autoLoadGroupKey();
        }
    }
    // nap tu file
    private void loadConfig() {
        try {
            File f = new File(FILE);
            if (f.exists()) {
                try (FileInputStream fis = new FileInputStream(f)) { p.load(fis); }
            }
        } catch (Exception e) {}
    }
    // tu dong nap file .grpkey neu co
    private void autoLoadGroupKey() {
        File folder = new File(".");
        File[] grpFiles = folder.listFiles((dir, name) -> name.endsWith(".grpkey"));
        if (grpFiles != null && grpFiles.length > 0) {
            Properties grpProps = new Properties();
            try (FileInputStream fis = new FileInputStream(grpFiles[0])) {
                grpProps.load(fis);
                String key = grpProps.getProperty("ONBOARDING_KEY");
                String ip = grpProps.getProperty("SERVER_IP");
                if (key != null && ip != null) {
                    p.setProperty("key", key);
                    p.setProperty("server_ip", ip);
                    saveConfig();
                    System.out.println(">> [Client] Đã nạp cấu hình từ file: " + grpFiles[0].getName());
                    System.out.println(">> [Client] Server IP: " + ip);
                }
            } catch (Exception e) {
                System.err.println(">> Lỗi nạp file .grpkey: " + e.getMessage());
            }
        }
    }
    // nhap file key do nguoi dung chon
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
                if (ip != null && !ip.isEmpty()) {
                    p.setProperty("server_ip", ip);
                } else {
                    p.setProperty("server_ip", "127.0.0.1");
                }
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
    // kiem tra ip mac dinh
    private boolean isDefaultIp() {
        String ip = p.getProperty("server_ip");
        return ip == null || ip.isEmpty() || ip.equals("127.0.0.1") || ip.equals("localhost");
    }

    public void saveToken(String t) {
        if (t == null) {
            p.remove("token");
        } else {
            p.setProperty("token", t);
        }
        saveConfig();
    }
    // luu cau hinh xuong file
    private void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(FILE)) {
            p.store(fos, "LanControl Client Config");
        } catch (Exception e) {}
    }

    public String getToken() { return p.getProperty("token"); }
    public String getKey() { return p.getProperty("key"); }

    // lay ip server
    public String getServerIp() {
        return p.getProperty("server_ip", "127.0.0.1");
    }
    public boolean isConfigured() {
        return (getToken() != null && !getToken().isEmpty()) ||
                (getKey() != null && !getKey().isEmpty());
    }
}