package com.lancontrol.server.gui;

import com.lancontrol.server.model.HeartbeatModel;
import com.lancontrol.server.model.ProcessInfo;
import com.lancontrol.server.network.ClientSession;
import com.lancontrol.server.network.SessionManager;
import com.lancontrol.server.service.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller chính điều khiển giao diện Server.
 * Quản lý danh sách máy trạm, thông số phần cứng và các cửa sổ chức năng.
 */
public class ServerController implements ScreenDataListener, HeartbeatListener, ProcessDataListener {

    // --- CÁC THÀNH PHẦN FXML (Phải khớp ID trong server_main.fxml) ---
    @FXML private Label cpuLabel, ramLabel, diskLabel, networkLabel, nameLabel, macLabel, ipLabel;
    @FXML private VBox deviceListContainer, groupContainer;
    @FXML private GridPane myGrid;

    // --- SERVICES ---
    private final CommandService commandService;
    private final SessionManager sessionManager;
    private final GroupService groupService;
    private final HeartbeatService heartbeatService;

    // --- QUẢN LÝ TRẠNG THÁI ---
    private final Map<Integer, FileExplorerController> activeFileExplorers = new HashMap<>();
    private final Map<Integer, ImageView> clientViews = new HashMap<>();
    private final Map<Integer, VBox> clientContainers = new HashMap<>();
    private final Map<Integer, HeartbeatModel> lastHeartbeats = new HashMap<>();
    private final Map<Integer, ProcessController> activeProcessWindows = new HashMap<>();
    private final Map<Integer, ScreenController> activeScreens = new HashMap<>();

    private Integer selectedClientId = null;

    public ServerController(CommandService cs, SessionManager sm, GroupService gs, HeartbeatService hs) {
        this.commandService = cs;
        this.sessionManager = sm;
        this.groupService = gs;
        this.heartbeatService = hs;
    }

    @FXML
    public void initialize() {
        // Đăng ký Listener để nhận dữ liệu từ tầng Network
        commandService.addScreenListener(this);
        commandService.addProcessListener(this);
        heartbeatService.addListener(this);

        refreshGroupList();
        System.out.println(">> [UI] Giao diện quản trị đã khởi tạo thành công.");
    }

    // --- 1. XỬ LÝ HÌNH ẢNH (GRID & STREAMING) ---

    @Override
    public void onScreenFrameReceived(int clientId, byte[] imageBytes) {
        if (imageBytes == null) return;

        // Ưu tiên cập nhật cửa sổ Streaming (nếu đang mở)
        ScreenController sc = activeScreens.get(clientId);
        if (sc != null) {
            sc.updateFrame(imageBytes);
        }

        // Cập nhật Thumbnail ở Grid chính
        Platform.runLater(() -> {
            ImageView view = clientViews.get(clientId);
            if (view == null) {
                setupNewClientUI(clientId);
                view = clientViews.get(clientId);
            }
            view.setImage(new Image(new ByteArrayInputStream(imageBytes)));
        });
    }

    private void setupNewClientUI(int clientId) {
        ClientSession session = sessionManager.get(clientId);
        String hostname = (session != null && session.getHostname() != null) ? session.getHostname() : "Client " + clientId;

        // Tạo Thumbnail
        ImageView iv = new ImageView();
        iv.setFitWidth(160);
        iv.setPreserveRatio(true);
        clientViews.put(clientId, iv);

        // Tạo Container cho Grid
        VBox container = new VBox(8);
        container.setAlignment(Pos.CENTER);
        container.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-border-color: #dddddd; -fx-border-radius: 5;");
        container.getChildren().addAll(iv, new Label(hostname));
        container.setOnMouseClicked(e -> selectClient(clientId));
        clientContainers.put(clientId, container);

        // Thêm vào Sidebar & Grid
        addDeviceToSidebar(clientId, hostname);
        int index = clientViews.size() - 1;
        myGrid.add(container, index % 4, index / 4);
    }

    // --- 2. XỬ LÝ SỰ KIỆN CLICK CHUỘT ---

    private void selectClient(int clientId) {
        if (selectedClientId != null && clientContainers.containsKey(selectedClientId)) {
            clientContainers.get(selectedClientId).setStyle("-fx-padding: 10; -fx-background-color: white; -fx-border-color: #dddddd;");
        }

        this.selectedClientId = clientId;

        if (clientContainers.containsKey(clientId)) {
            clientContainers.get(clientId).setStyle("-fx-padding: 9; -fx-background-color: #e3f2fd; -fx-border-color: #0078d7; -fx-border-width: 2;");
        }

        ClientSession session = sessionManager.get(clientId);
        if (session != null) {
            nameLabel.setText("Name: " + session.getHostname());
            ipLabel.setText("IP: " + session.getIpAddress());
            macLabel.setText("MAC: " + session.getMacAddress());
        }

        HeartbeatModel hb = lastHeartbeats.get(clientId);
        if (hb != null) updateHardwareLabels(hb);
        else resetDynamicLabels();
    }

    // --- 3. CÁC HÀNH ĐỘNG ĐIỀU KHIỂN (ON ACTION) ---

    @FXML
    public void onControlClick() { // Nút Remote Desktop
        if (selectedClientId == null) return;

        if (activeScreens.containsKey(selectedClientId)) {
            activeScreens.get(selectedClientId).getStage().toFront();
            return;
        }

        try {
            URL fxmlLoc = getClass().getResource("/screen_view.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlLoc);
            Parent root = loader.load();

            ScreenController sc = loader.getController();
            sc.initData(selectedClientId, sessionManager.get(selectedClientId).getHostname());

            Stage stage = new Stage();
            stage.setTitle("Remote View - " + selectedClientId);
            stage.setScene(new Scene(root));
            sc.setStage(stage);

            activeScreens.put(selectedClientId, sc);
            stage.setOnHidden(e -> {
                activeScreens.remove(selectedClientId);
                commandService.stopScreenStream(selectedClientId);
            });

            stage.show();
            commandService.startScreenStream(selectedClientId, true);
        } catch (IOException e) {
            showError("Không thể mở cửa sổ xem màn hình: " + e.getMessage());
        }
    }

    @FXML
    public void onProcessClick() { // Nút Processes
        if (selectedClientId == null) return;

        if (activeProcessWindows.containsKey(selectedClientId)) {
            activeProcessWindows.get(selectedClientId).getStage().toFront();
            return;
        }

        try {
            URL fxmlLoc = getClass().getResource("/process_view.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlLoc);
            Parent root = loader.load();

            ProcessController pc = loader.getController();
            pc.initData(selectedClientId, commandService);

            Stage stage = new Stage();
            stage.setTitle("Process Manager - " + selectedClientId);
            stage.setScene(new Scene(root));
            pc.setStage(stage);

            activeProcessWindows.put(selectedClientId, pc);
            stage.setOnHidden(e -> activeProcessWindows.remove(selectedClientId));

            stage.show();
            commandService.requestProcessList(selectedClientId);
        } catch (IOException e) {
            showError("Không thể mở trình quản lý tiến trình: " + e.getMessage());
        }
    }

    @FXML
    public void onShutdownClick() {
        if (selectedClientId == null) return;
        confirmAction("Xác nhận tắt máy ID: " + selectedClientId + "?", () -> {
            commandService.sendShutdown(selectedClientId);
        });
    }

    @FXML
    public void onSleepClick() {
        if (selectedClientId == null) return;
        commandService.sendSleep(selectedClientId);
    }

    @FXML
    public void onAddGroup() {
        System.out.println(">> [UI] Chức năng thêm nhóm đang được phát triển.");
    }

    // --- 4. CẬP NHẬT DỮ LIỆU TỪ HEARTBEAT & PROCESS ---

    @Override
    public void onHeartbeatReceived(int clientId, HeartbeatModel hb) {
        lastHeartbeats.put(clientId, hb);
        if (selectedClientId != null && selectedClientId == clientId) {
            updateHardwareLabels(hb);
        }
    }

    @Override
    public void onProcessListReceived(int clientId, List<ProcessInfo> processes) {
        ProcessController pc = activeProcessWindows.get(clientId);
        if (pc != null) {
            pc.updateTable(processes);
        }
    }

    // --- 5. HÀM HỖ TRỢ UI ---

    private void updateHardwareLabels(HeartbeatModel hb) {
        Platform.runLater(() -> {
            cpuLabel.setText("CPU: " + hb.getCpuUsage() + "%");
            ramLabel.setText("RAM: " + hb.getRamUsage() + "%");
            diskLabel.setText("DISK: " + hb.getDiskFreePercent() + "%");
            networkLabel.setText("NET: ↑" + hb.getNetSentRateKb() + " / ↓" + hb.getNetRecvRateKb() + " KB/s");
        });
    }

    private void resetDynamicLabels() {
        cpuLabel.setText("CPU: --"); ramLabel.setText("RAM: --");
        diskLabel.setText("DISK: --"); networkLabel.setText("NET: --");
    }

    private void addDeviceToSidebar(int clientId, String name) {
        Button btn = new Button("● " + name);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle("-fx-alignment: CENTER_LEFT; -fx-background-color: transparent; -fx-text-fill: #2e7d32; -fx-cursor: hand;");
        btn.setOnAction(e -> selectClient(clientId));
        deviceListContainer.getChildren().add(btn);
    }

    private void refreshGroupList() {
        Platform.runLater(() -> {
            groupContainer.getChildren().clear();
            groupContainer.getChildren().addAll(new Label("• Phòng máy 01"), new Label("• Phòng máy 02"));
        });
    }

    private void showError(String msg) {
        System.err.println(">> [UI Error] " + msg);
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, msg);
            alert.show();
        });
    }

    private void confirmAction(String msg, Runnable action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, msg);
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) action.run();
        });
    }
    @FXML
    public void onFileExplorerClick() {
        if (selectedClientId == null) return;

        // Nếu đã mở cửa sổ rồi thì mang lên phía trước
        if (activeFileExplorers.containsKey(selectedClientId)) {
            activeFileExplorers.get(selectedClientId).getStage().toFront();
            return;
        }

        try {
            URL fxmlLoc = getClass().getResource("/file_manager.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlLoc);
            Parent root = loader.load();

            FileExplorerController fec = loader.getController();
            fec.initData(selectedClientId, commandService);

            Stage stage = new Stage();
            stage.setTitle("File Explorer - " + selectedClientId);
            stage.setScene(new Scene(root));
            fec.setStage(stage); // Đảm bảo Controller có hàm setStage

            // Lưu vào danh sách quản lý
            activeFileExplorers.put(selectedClientId, fec);

            // Đăng ký với CommandService để nhận dữ liệu file list
            commandService.addFileExplorer(selectedClientId, fec);

            stage.setOnHidden(e -> {
                activeFileExplorers.remove(selectedClientId);
                commandService.removeFileExplorerListener(selectedClientId);
            });

            stage.show();
            commandService.requestFileTree(selectedClientId, "");
        } catch (IOException e) {
            showError("Không thể mở trình quản lý tệp tin: " + e.getMessage());
        }
    }


    @FXML
    public void onSendFileClick() {
        if (selectedClientId == null) return;

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Chọn file gửi tới Client");
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            commandService.sendDownloadRequest(selectedClientId, selectedFile);
        }
    }

}