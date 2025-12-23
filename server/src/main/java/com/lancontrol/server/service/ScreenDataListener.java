package com.lancontrol.server.service;

public interface ScreenDataListener {
    void onScreenFrameReceived(int clientId, byte[] imageBytes);
}