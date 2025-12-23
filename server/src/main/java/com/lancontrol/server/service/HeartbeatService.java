package com.lancontrol.server.service;
import com.lancontrol.server.db.ClientDeviceDAO;
import com.lancontrol.server.model.HeartbeatModel;
public class HeartbeatService {
    private final ClientDeviceDAO dao = new ClientDeviceDAO();
    public void process(int cid, HeartbeatModel hb) { dao.updateHeartbeat(cid, hb); }
}