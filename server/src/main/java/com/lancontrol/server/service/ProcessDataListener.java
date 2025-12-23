package com.lancontrol.server.service;

import com.lancontrol.server.model.ProcessInfo;

import java.util.List;

public interface ProcessDataListener {
    void onProcessListReceived(int clientId, List<ProcessInfo> processes);
}
