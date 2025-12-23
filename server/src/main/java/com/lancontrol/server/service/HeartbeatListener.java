package com.lancontrol.server.service;

import com.lancontrol.server.model.HeartbeatModel;

public interface HeartbeatListener {
    void onHeartbeatReceived(int clientId, HeartbeatModel hb);
}
