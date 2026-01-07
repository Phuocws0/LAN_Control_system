package com.lancontrol.server.network;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

public class ClientSession {
    private final int clientId;
    private final int groupId;
    private final Socket socket;
    private final DataOutputStream dos;


    private String hostname;
    private String ipAddress;
    private String macAddress;

    private final AtomicLong sequenceCounter = new AtomicLong(0);

    public ClientSession(int clientId, int groupId, Socket socket, DataOutputStream existingDos) throws IOException {
        this.clientId = clientId;
        this.groupId = groupId;
        this.socket = socket;
        this.dos = (existingDos != null) ? existingDos : new DataOutputStream(socket.getOutputStream());
    }
    // gui du lieu dang json den client
    public synchronized void send(String json) {
        if (json == null || socket.isClosed()) return;
        try {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            dos.writeByte(0x01);
            dos.writeInt(data.length);
            dos.write(data);
            dos.flush();
        } catch (IOException e) {
            System.err.println(">> [ClientSession] Lỗi gửi dữ liệu tới ID " + clientId + ": " + e.getMessage());
        }
    }

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

        }
    }
}