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
                if (config.importKeyFile(selectedFile)) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Cấu hình thành công! Máy sẽ kết nối tới: " + config.getServerIp());
                    alert.showAndWait();

                    startBackgroundService();
                    showStatusWindow(stage);
                } else {
                    lblStatus.setText("File không hợp lệ hoặc lỗi đọc file!");
                }
            }
        });

        root.getChildren().addAll(lblTitle, lblInstruction, btnSelect, lblStatus);
        stage.setTitle("LanControl Client Setup");
        stage.setScene(new Scene(root, 350, 200));
        stage.show();
    }

    private void showStatusWindow(Stage stage) {
        VBox root = new VBox(15);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-padding: 20; -fx-background-color: #f9f9f9;");

        Label lblStatus = new Label("Client đang hoạt động");
        lblStatus.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: green;");

        Label lblIp = new Label("Server IP: " + config.getServerIp());
        String key = config.getKey();
        Label lblGroup = new Label("Key hiện tại: ... " + (key != null && key.length() > 5 ? key.substring(0, 5) : "N/A"));

        Button btnChangeKey = new Button("🔄 Đổi Nhóm / Nhập Key Mới");
        btnChangeKey.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-cursor: hand;");

        btnChangeKey.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Chọn file Key mới (.grpkey)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Group Key File", "*.grpkey"));
            File selectedFile = fileChooser.showOpenDialog(stage);

            if (selectedFile != null) {
                if (config.importKeyFile(selectedFile)) {
                    config.saveToken(null);
                    restartBackgroundService();

                    lblIp.setText("Server IP: " + config.getServerIp());
                    lblGroup.setText("Key mới: ... " + config.getKey().substring(0, 5));

                    new Alert(Alert.AlertType.INFORMATION, "Đã cập nhật Key mới! Client đang kết nối lại...").show();
                } else {
                    new Alert(Alert.AlertType.ERROR, "File Key không hợp lệ!").show();
                }
            }
        });

        root.getChildren().addAll(lblStatus, lblIp, lblGroup, btnChangeKey);
        stage.setTitle("LanControl Client");
        stage.setScene(new Scene(root, 350, 200));
        stage.show();
    }


    private void startBackgroundService() {
        clientThread = new Thread(() -> {
            try {
                socketClient = new SocketClient();
                socketClient.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private void restartBackgroundService() {
        System.out.println(">> [ClientApp] Đang khởi động lại dịch vụ...");

        if (socketClient != null) {
            socketClient.close();
        }
        if (clientThread != null) {
            clientThread.interrupt();
        }

        startBackgroundService();
    }


    @Override
    public void stop() throws Exception {
        if (socketClient != null) {
            socketClient.close();
        }
        super.stop();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}