package com.lancontrol.server.network;

import com.lancontrol.server.model.*;
import com.lancontrol.server.service.*;
import com.lancontrol.server.util.JsonUtil;
import com.lancontrol.server.util.SecurityUtil;
import java.io.*;
import java.net.Socket;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final SessionManager sessionMgr;
    private final AuthService authService;
    private final HeartbeatService hbService;
    private final CommandService commandService;
    private ClientSession session;
    private long lastClientSequence = -1;
    private long serverSequence = 0;

    public ClientHandler(Socket s, SessionManager sm, AuthService as, HeartbeatService hs, CommandService cs) {
        this.socket = s;
        this.sessionMgr = sm;
        this.authService = as;
        this.hbService = hs;
        this.commandService = cs;
    }

    @Override
    public void run() {
        try (
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            socket.setSoTimeout(60000);
            // xu ly ket noi
            String line = in.readLine();
            NetworkPacket p = decryptAndVerify(line);
            if (p == null) return;
            if ("ONBOARDING".equals(p.getCommand())) {
                OnboardingPayload pl = JsonUtil.fromJson(p.getPayloadJson(), OnboardingPayload.class);
                AuthResponse resp = authService.processOnboarding(pl, pl.getCurrentIp());
                //phan hoi ket qua dang ky
                sendSecure("ONBOARDING_RESPONSE", null, resp, out);
                return;
            } else if ("AUTH_DAILY".equals(p.getCommand())) {
                ClientDevice dev = authService.getClientByToken(p.getToken());
                if (dev != null) {
                    session = new ClientSession(dev.getClientId(), dev.getGroupId(), socket, out);
                    sessionMgr.add(session);
                    sendSecure("AUTH_SUCCESS", null, "Access Granted", out);
                } else {
                    return;
                }
            } else {
                return;
            }
            // Bat dau vong lap nhan du lieu tu client
            while ((line = in.readLine()) != null) {
                if (line.length() > 5 * 1024 * 1024) throw new IOException("Packet size limit exceeded");
                NetworkPacket pkt = decryptAndVerify(line);
                if (pkt == null) continue;
                if ("HEARTBEAT".equals(pkt.getCommand())) {
                    HeartbeatModel hb = JsonUtil.fromJson(pkt.getPayloadJson(), HeartbeatModel.class);
                    hbService.process(session.getClientId(), hb);
                } else {
                    commandService.handleClientResponse(session.getClientId(), pkt);
                }
            }
        } catch (Exception e) {
        } finally {
            if (session != null) sessionMgr.remove(session.getClientId());
            try {
                if (!socket.isClosed()) socket.close();
            } catch (IOException e) {}
        }
    }
    // Giai ma AES Payload va xac thuc HMAC tu Client
    private NetworkPacket decryptAndVerify(String line) {
        try {
            if (line == null) return null;
            NetworkPacket p = JsonUtil.fromJson(line, NetworkPacket.class);
            if (p == null) return null;
            String signData = p.getCommand() + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
            String calculatedHmac = SecurityUtil.generateHMAC(signData);
            if (!calculatedHmac.equals(p.getHmac())) {
                System.err.println(">> [Security] HMAC mismatch! Packet dropped.");
                return null;
            }
            //check replay attack
            if (p.getSequenceNumber() <= lastClientSequence) {
                System.err.println(">> [Security] Replay Attack detected!");
                return null;
            }
            lastClientSequence = p.getSequenceNumber();
            // Giai ma AES Payload
            if (p.getPayloadJson() != null && !p.getPayloadJson().isEmpty()) {
                p.setPayloadJson(SecurityUtil.decrypt(p.getPayloadJson()));
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }
    private void sendSecure(String cmd, String token, Object payload, PrintWriter out) {
        try {
            NetworkPacket p = new NetworkPacket();
            p.setCommand(cmd);
            p.setToken(token);
            p.setTimestamp(System.currentTimeMillis());
            p.setSequenceNumber(++serverSequence);
            //ma hoa AES payload
            String jsonPayload = (payload instanceof String) ? (String) payload : JsonUtil.toJson(payload);
            p.setPayloadJson(SecurityUtil.encrypt(jsonPayload));
            //tao chu ky HMAC
            String signData = cmd + p.getPayloadJson() + p.getTimestamp() + p.getSequenceNumber();
            p.setHmac(SecurityUtil.generateHMAC(signData));

            out.println(JsonUtil.toJson(p));
        } catch (Exception e) {
            System.err.println(">> [Security] Error encrypting response: " + e.getMessage());
        }
    }
}