package com.lancontrol.client.network;

import com.lancontrol.client.config.ConfigManager;
import com.lancontrol.client.model.FileTransferRequest;
import java.io.*;
import java.net.Socket;

public class FileDownloader {
    private final String serverIp;
    private final ConfigManager cfg = new ConfigManager();
    private static final int FILE_PORT = 9998;
    private static final String DOWNLOAD_DIR = "C:\\LanControlDownloads\\";

    public FileDownloader(String serverIp) {
        this.serverIp = serverIp;
    }

    public void downloadFile(FileTransferRequest req) {
        new Thread(() -> {
            File dir = new File(DOWNLOAD_DIR);
            if (!dir.exists()) dir.mkdirs();

            try (Socket socket = new Socket(serverIp, FILE_PORT);
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {
                dos.writeUTF("MODE_DOWNLOAD");
                dos.writeUTF(cfg.getToken());
                dos.flush();

                String authStatus = dis.readUTF();
                if (!"OK".equals(authStatus)) return;
                dos.writeUTF(req.getFileId());
                dos.flush();
                long fileSize = dis.readLong();
                if (fileSize == -1) return;

                File saveFile = new File(DOWNLOAD_DIR + req.getFileName());
                try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;
                    while (totalRead < fileSize && (bytesRead = dis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }
                System.out.println(">> [FileDownloader] Đã tải xong: " + saveFile.getName());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}