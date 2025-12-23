package com.lancontrol.server.network;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Quản lý thông tin kết nối và luồng gửi dữ liệu cho từng Client riêng biệt.
 */
public class ClientSession {
    private final int clientId;
    private final int groupId;
    private final Socket socket;
    private final DataOutputStream dos; // Sử dụng Binary Stream thay cho PrintWriter

    // Thông tin định danh hiển thị trên UI
    private String hostname;
    private String ipAddress;
    private String macAddress;

    // Quản lý số thứ tự gói tin để chống Replay Attack (Server -> Client)
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    public ClientSession(int clientId, int groupId, Socket socket, DataOutputStream existingDos) throws IOException {
        this.clientId = clientId;
        this.groupId = groupId;
        this.socket = socket;
        // Nếu đã khởi tạo dos ở ClientHandler thì dùng lại, nếu chưa thì tạo mới
        this.dos = (existingDos != null) ? existingDos : new DataOutputStream(socket.getOutputStream());
    }

    /**
     * Gửi dữ liệu xuống Client theo giao thức Binary Framing.
     * @param json Gói tin NetworkPacket dạng chuỗi JSON.
     */
    public synchronized void send(String json) {
        if (json == null || socket.isClosed()) return;

        try {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);

            // Cấu trúc khung Binary: [Type: 1 byte] [Size: 4 bytes] [Payload: N bytes]
            dos.writeByte(0x01);          // Type 0x01: JSON/Command
            dos.writeInt(data.length);     // Kích thước gói tin
            dos.write(data);               // Dữ liệu thực tế

            dos.flush();                   // Ép dữ liệu đi ngay lập tức để giảm lag
        } catch (IOException e) {
            System.err.println(">> [ClientSession] Lỗi gửi dữ liệu tới ID " + clientId + ": " + e.getMessage());
        }
    }

    // --- GETTERS & SETTERS ---

    public long getNextSequence() {
        return sequenceCounter.incrementAndGet();
    }

    public int getClientId() { return clientId; }
    public int getGroupId() { return groupId; }
    public Socket getSocket() { return socket; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public void close() {
        try {
            if (dos != null) dos.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
}