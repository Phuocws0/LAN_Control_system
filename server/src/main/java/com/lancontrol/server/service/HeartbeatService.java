package com.lancontrol.server.service;
import com.lancontrol.server.db.ClientDeviceDAO;
import com.lancontrol.server.model.HeartbeatModel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HeartbeatService {
    private final ClientDeviceDAO dao = new ClientDeviceDAO();
    public void process(int cid, HeartbeatModel hb) { dao.updateHeartbeat(cid, hb); }
    private final List<HeartbeatListener> listeners = new CopyOnWriteArrayList<>();
    public void addListener(HeartbeatListener listener) {
        listeners.add(listener);
    }
    public void removeListener(HeartbeatListener listener) {
        listeners.remove(listener);
    }
    public void notifyListeners(int cid, HeartbeatModel hb) {
        for (HeartbeatListener listener : listeners) {
            listener.onHeartbeatReceived(cid, hb);
        }
    }
    public void processHeartbeat(int clientId, HeartbeatModel hb) {
        // Duyệt qua danh sách và báo cho các listener (như ServerController) cập nhật UI
        for (HeartbeatListener listener : listeners) {
            listener.onHeartbeatReceived(clientId, hb);
        }
    }

}