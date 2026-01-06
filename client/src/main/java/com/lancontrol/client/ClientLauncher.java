package com.lancontrol.client;

public class ClientLauncher {
    public static void main(String[] args) {
        // Gọi đến hàm main của ClientApp
        // Kỹ thuật này đánh lừa JVM để nó nạp các thư viện JavaFX sau khi ứng dụng bắt đầu
        ClientApp.main(args);
    }
}