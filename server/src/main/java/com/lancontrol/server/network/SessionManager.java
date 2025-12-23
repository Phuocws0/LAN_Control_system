package com.lancontrol.server.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SessionManager {
    private final Map<Integer, ClientSession> sessions = new ConcurrentHashMap<>();

    public void add(ClientSession s) {
        sessions.put(s.getClientId(), s);
    }

    public void remove(int cid) {
        sessions.remove(cid);
    }

    public ClientSession get(int cid) {
        return sessions.get(cid);
    }

    public List<Integer> getAllClientIds() {
        return new ArrayList<>(sessions.keySet());
    }

    public List<Integer> getClientIdsByGroup(int groupId) {
        return sessions.values().stream()
                .filter(s -> s.getGroupId() == groupId)
                .map(ClientSession::getClientId)
                .collect(Collectors.toList());
    }
}