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

    // cap nhat frame
    public void updateFrame(byte[] imageBytes) {
        if (imageBytes == null) return;
        Platform.runLater(() -> {
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                Image img = new Image(bais);
                if (!img.isError()) {
                    screenView.setImage(img);
                    calculateFPS();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    // tinh toan fps
    private void calculateFPS() {
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFrameTime >= 1000) {
            fpsLabel.setText("FPS: " + frameCount);
            frameCount = 0;
            lastFrameTime = now;
        }
    }

    @FXML private void onRefresh() { }
    @FXML private void onToggleControl() { }

    public void setStage(Stage stage) { this.stage = stage; }
    public Stage getStage() { return stage; }
}