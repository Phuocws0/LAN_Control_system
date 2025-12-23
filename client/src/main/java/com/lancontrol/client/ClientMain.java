package com.lancontrol.client;
import com.lancontrol.client.network.SocketClient;
public class ClientMain {
    public static void main(String[] args) {
        new SocketClient().start();
    }
}