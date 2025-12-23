package com.lancontrol.server.network;

import java.io.PrintWriter;
import java.net.Socket;

public class ClientSession {
    private final int clientId;
    private final int groupId;
    private final Socket socket;
    private final PrintWriter writer;
    private long serverSequence = 0;
    public ClientSession(int cid, int gid, Socket s, PrintWriter w) {
        this.clientId = cid;
        this.groupId = gid;
        this.socket = s;
        this.writer = w;
    }
    public synchronized long getNextSequence() {
        return ++serverSequence;
    }
    public int getClientId() { return clientId; }
    public int getGroupId() { return groupId; } // [THÊM MỚI]

    public void send(String json) {
        if(!socket.isClosed()) writer.println(json);
    }
}