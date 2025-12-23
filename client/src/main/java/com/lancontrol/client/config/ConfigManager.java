package com.lancontrol.client.config;
import java.io.*;
import java.util.Properties;
public class ConfigManager {
    private static final String FILE = "client.properties";
    private Properties p = new Properties();

    public ConfigManager() {
        try { if(new File(FILE).exists()) p.load(new FileInputStream(FILE)); } catch(Exception e){}
    }
    public void saveToken(String t) {
        p.setProperty("token", t);
        try { p.store(new FileOutputStream(FILE), null); } catch(Exception e){}
    }
    public String getToken() { return p.getProperty("token"); }
    public String getKey() { return p.getProperty("key", "KEY_ABC123"); } // Key mặc định để test
}