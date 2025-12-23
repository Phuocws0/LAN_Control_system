package com.lancontrol.server.network;

import com.lancontrol.server.service.AuthService;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileServer {
    private static final int FILE_PORT = 9998;
    private final Map<String, File> pendingFiles = new ConcurrentHashMap<>();
    private final AuthService authService; // Thêm dịch vụ xác thực [cite: 510]

    public FileServer(AuthService authService) {
        this.authService = authService;
    }

    public void start() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(FILE_PORT)) {
                System.out.println(">> [FileServer] Đang lắng nghe cổng " + FILE_PORT);
                while (true) {
                    Socket socket = serverSocket.accept();
                    new Thread(() -> handleFileTransfer(socket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void registerFile(String fileId, File file) {
        pendingFiles.put(fileId, file);
    }

    private void handleFileTransfer(Socket socket) {
        try (
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream())
        ) {
            socket.setSoTimeout(60000);
            String mode = dis.readUTF();
            String token = dis.readUTF();
            if (authService.getClientByToken(token) == null) {
                dos.writeUTF("AUTH_FAILED");
                return;
            }
            dos.writeUTF("OK");

            if ("MODE_UPLOAD".equals(mode)) {
                String fileName = dis.readUTF();
                long fileSize = dis.readLong();

                File uploadDir = new File("uploads");
                if (!uploadDir.exists()) uploadDir.mkdirs();

                File targetFile = new File(uploadDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalRead = 0;
                    int lastPercent = 0;
                    while (totalRead < fileSize && (bytesRead = dis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        // Tính toán phần trăm (%)
                        int currentPercent = (int) ((totalRead * 100) / fileSize);
                        if (currentPercent > lastPercent) {
                            lastPercent = currentPercent;
                            System.out.println(">> [FileServer] Đang nhận: " + fileName + " (" + currentPercent + "%)");
                        }
                    }
                }
                System.out.println(">> [FileServer] Đã nhận file: " + targetFile.getAbsolutePath());

            } else if ("MODE_DOWNLOAD".equals(mode)) {
                String fileId = dis.readUTF();
                File fileToSend = pendingFiles.get(fileId);

                if (fileToSend != null && fileToSend.exists()) {
                    dos.writeLong(fileToSend.length());
                    dos.flush();

                    try (FileInputStream fis = new FileInputStream(fileToSend)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            dos.write(buffer, 0, bytesRead);
                        }
                    }
                    dos.flush();
                    pendingFiles.remove(fileId);
                } else {
                    dos.writeLong(-1);
                }
            }
        } catch (IOException e) {
            System.err.println(">> [FileServer] Lỗi: " + e.getMessage());
        }
    }
}