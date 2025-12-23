package com.lancontrol.client.network;

import com.lancontrol.client.config.ConfigManager;
import java.io.*;
import java.net.Socket;

public class FileUploader {
    private final String serverIp;
    private final ConfigManager cfg = new ConfigManager();
    private static final int FILE_PORT = 9998;

    public FileUploader(String serverIp) {
        this.serverIp = serverIp;
    }

    public void uploadFile(String filePath) {
        new Thread(() -> {
            File file = new File(filePath);
            if (!file.exists()) return;

            try (Socket socket = new Socket(serverIp, FILE_PORT);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {

                dos.writeUTF("MODE_UPLOAD");
                dos.writeUTF(cfg.getToken());
                dos.flush();
                String authStatus = dis.readUTF();
                if (!"OK".equals(authStatus)) {
                    System.err.println(">> [FileUploader] Lỗi xác thực với Server.");
                    return;
                }
                dos.writeUTF(file.getName());
                dos.writeLong(file.length());

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, bytesRead);
                    }
                }
                dos.flush();
                System.out.println(">> [FileUploader] Upload thành công.");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}