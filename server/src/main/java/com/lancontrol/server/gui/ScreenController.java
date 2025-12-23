package com.lancontrol.server.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import java.io.ByteArrayInputStream;

public class ScreenController {
    @FXML private ImageView screenView;
    @FXML private Label fpsLabel;
    @FXML private Label statusLabel;

    private int clientId;
    private Stage stage;
    private long lastFrameTime = 0;
    private int frameCount = 0;

    public void initData(int clientId, String hostname) {
        this.clientId = clientId;
        Platform.runLater(() -> statusLabel.setText("Máy: " + hostname + " (ID: " + clientId + ")"));
    }

    // Hàm này sẽ được gọi mỗi khi có frame ảnh mới về
    public void updateFrame(byte[] imageBytes) {
        if (imageBytes == null) return;

        // Sử dụng cơ chế kiểm tra để bỏ qua các Frame cũ nếu Frame mới đã tới
        Platform.runLater(() -> {
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                Image img = new Image(bais);

                // Chỉ cập nhật nếu Image load thành công và không bị lỗi
                if (!img.isError()) {
                    screenView.setImage(img);
                    calculateFPS();
                }
            } catch (Exception e) {
                // Bỏ qua frame lỗi để tránh nháy đen màn hình
            }
        });
    }

    private void calculateFPS() {
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFrameTime >= 1000) {
            fpsLabel.setText("FPS: " + frameCount);
            frameCount = 0;
            lastFrameTime = now;
        }
    }

    @FXML private void onRefresh() { /* Gửi lại lệnh REQ_START_SCREEN_STREAM */ }
    @FXML private void onToggleControl() { /* Logic bật tắt điều khiển chuột */ }

    public void setStage(Stage stage) { this.stage = stage; }
    public Stage getStage() { return stage; }
}