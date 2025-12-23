package com.lancontrol.server.network;

import com.lancontrol.server.service.*;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        int PORT = 9999;
        ExecutorService pool = Executors.newCachedThreadPool();
        SessionManager sm = new SessionManager();
        AuthService as = new AuthService();
        HeartbeatService hs = new HeartbeatService();
        FileServer fileServer = new FileServer(as);
        fileServer.start();
        System.out.println("FileServer started on port 9998");
        CommandService cs = new CommandService(sm, fileServer);
        System.out.println("Server listening on " + PORT);
        try (ServerSocket ss = new ServerSocket(PORT)) {
            while (true) {
                pool.submit(new ClientHandler(ss.accept(), sm, as, hs, cs) );
            }
        }
    }
}