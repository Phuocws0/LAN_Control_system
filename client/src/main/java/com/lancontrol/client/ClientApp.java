package com.lancontrol.client;

import com.lancontrol.client.config.ConfigManager;
import com.lancontrol.client.network.SocketClient;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class ClientApp extends Application {

    private ConfigManager config;
    private SocketClient socketClient;
    private Thread clientThread;
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        config = new ConfigManager();

        if (config.isConfigured()) {
            startBackgroundService();
            showStatusWindow(primaryStage);
        } else {
            showSetupWindow(primaryStage);
        }
    }

    private void showSetupWindow(Stage stage) {
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20; -fx-background-color: #f4f4f4;");

        Label lblTitle = new Label("CẤU HÌNH THIẾT BỊ");
        lblTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333;");

        Label lblInstruction = new Label("Vui lòng chọn file (.grpkey) do quản trị viên cung cấp.");
        lblInstruction.setWrapText(true);

        Button btnSelect = new Button("📂 Chọn File Kích Hoạt");
        btnSelect.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        Label lblStatus = new Label("");
        lblStatus.setStyle("-fx-text-fill: red;");

        btnSelect.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Chọn file khóa nhóm (.grpkey)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Group Key File", "*.grpkey"));
            File selectedFile = fileChooser.showOpenDialog(stage);

            if (selectedFile != null) {
                boolean success = config.importKeyFile(selectedFile);
                if (success) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Cấu hình thành công! Máy sẽ kết nối tới: " + config.getServerIp());
                    alert.showAndWait();

                    // Chuyển sang chạy service
                    startBackgroundService();
                    showStatusWindow(stage);
                } else {
                    lblStatus.setText("File không hợp lệ hoặc lỗi đọc file!");
                }
            }
        });

        root.getChildren().addAll(lblTitle, lblInstruction, btnSelect, lblStatus);
        Scene scene = new Scene(root, 350, 200);

        stage.setTitle("LanControl Client Setup");
        stage.setScene(scene);
        stage.show();
    }

    private void showStatusWindow(Stage stage) {
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20; -fx-background-color: #f9f9f9;");

        Label lblStatus = new Label("Client đang hoạt động");
        lblStatus.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: green;");

        Label lblIp = new Label("Server IP: " + config.getServerIp());
        Label lblGroup = new Label("Key hiện tại: ... " + (config.getKey() != null && config.getKey().length() > 5 ? config.getKey().substring(0, 5) : "N/A"));

        // NÚT NHẬP KEY MỚI
        Button btnChangeKey = new Button("🔄 Đổi Nhóm / Nhập Key Mới");
        btnChangeKey.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-cursor: hand;");

        btnChangeKey.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Chọn file Key mới (.grpkey)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Group Key File", "*.grpkey"));
            File selectedFile = fileChooser.showOpenDialog(stage);

            if (selectedFile != null) {
                // 1. Nạp file mới
                boolean success = config.importKeyFile(selectedFile);
                if (success) {
                    // 2. QUAN TRỌNG: Xóa Token cũ để ép đăng ký lại
                    config.saveToken(null);

                    // 3. Khởi động lại Service
                    restartBackgroundService();

                    // Cập nhật giao diện
                    lblIp.setText("Server IP: " + config.getServerIp());
                    lblGroup.setText("Key mới: ... " + config.getKey().substring(0, 5));

                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Đã cập nhật Key mới! Client đang kết nối lại...");
                    alert.show();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "File Key không hợp lệ!");
                    alert.show();
                }
            }
        });

        root.getChildren().addAll(lblStatus, lblIp, lblGroup, btnChangeKey);

        stage.setScene(new Scene(root, 350, 200));
        stage.setTitle("LanControl Client");
        stage.show();
    }
    private void restartBackgroundService() {
        System.out.println(">> [ClientApp] Đang khởi động lại dịch vụ...");

        // Ngắt kết nối cũ (nếu SocketClient có hàm stop/close thì gọi ở đây)
        // Cách đơn giản nhất: Vì SocketClient chạy while(true), ta cần cơ chế dừng nó.
        // Tuy nhiên, vì Thread.stop() bị deprecated, cách an toàn là interrupt
        // hoặc để đơn giản cho bạn: Ta chỉ cần tạo luồng mới, luồng cũ sẽ tự chết khi socket timeout hoặc lỗi auth.

        // Tốt nhất: SocketClient nên có hàm shutdown() để đóng socket.
        // Giả sử SocketClient của bạn có biến 'Socket s', ta ép đóng nó.
        if (socketClient != null) {
            socketClient.close();
        }

        // Ngắt luồng cũ
        if (clientThread != null) {
            clientThread.interrupt();
        }

        // Chạy luồng mới
        startBackgroundService();
    }
    private void startBackgroundService() {
        // Chạy SocketClient trên luồng riêng để không treo UI
        new Thread(() -> {
            try {
                // Đảm bảo SocketClient của bạn đã dùng code mới (lấy IP từ config)
                socketClient = new SocketClient();
                socketClient.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        System.exit(0); // Đảm bảo tắt hết luồng khi đóng app
    }
}