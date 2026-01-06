package com.lancontrol.server.gui;

import com.lancontrol.server.db.ClientDeviceDAO;
import com.lancontrol.server.model.ClientDevice;
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
import java.util.Optional;

public class ServerController implements ScreenDataListener, HeartbeatListener, ProcessDataListener {

    // --- CÁC THÀNH PHẦN FXML (Khớp với file FXML bạn gửi) ---
    @FXML private Label cpuLabel, ramLabel, diskLabel, networkLabel, nameLabel, macLabel, ipLabel;
    @FXML private VBox deviceListContainer; // Danh sách máy dạng text bên trái (nếu cần dùng)
    @FXML private VBox groupContainer;      // Danh sách nhóm bên trái
    @FXML private GridPane myGrid;          // Lưới hiển thị máy trạm ở giữa
    @FXML private Label osLabel, cpuModelLabel, ramTotalLabel, diskTotalLabel;

    // --- SERVICES ---
    private final CommandService commandService;
    private final SessionManager sessionManager;
    private final GroupService groupService;
    private final HeartbeatService heartbeatService;

    // --- STATE ---
    private Integer selectedGroupId = null; // ID nhóm đang chọn (null = Tất cả)
    private Integer selectedClientId = null; // ID máy đang chọn

    // --- QUẢN LÝ CỬA SỔ CON & UPDATE UI ---
    private final Map<Integer, FileExplorerController> activeFileExplorers = new HashMap<>();
    private final Map<Integer, ProcessController> activeProcessWindows = new HashMap<>();
    private final Map<Integer, ScreenController> activeScreens = new HashMap<>();

    private final Map<Integer, ImageView> clientViews = new HashMap<>(); // Cache ảnh thumbnail
    private final Map<Integer, VBox> clientContainers = new HashMap<>(); // Cache ô giao diện

    private final ClientDeviceDAO deviceDAO = new ClientDeviceDAO();

    public ServerController(CommandService cs, SessionManager sm, GroupService gs, HeartbeatService hs) {
        this.commandService = cs;
        this.sessionManager = sm;
        this.groupService = gs;
        this.heartbeatService = hs;
    }

    @FXML
    public void initialize() {
        // Đăng ký nhận sự kiện
        commandService.addScreenListener(this);
        commandService.addProcessListener(this);
        heartbeatService.addListener(this);

        // Tải dữ liệu lần đầu
        refreshGroupList();
        loadClientList();

        System.out.println(">> [UI] Giao diện quản trị đã khởi tạo.");
    }

    // =========================================================================
    // 1. QUẢN LÝ NHÓM (HIỂN THỊ & SỰ KIỆN)
    // =========================================================================

    private void refreshGroupList() {
        Platform.runLater(() -> {
            groupContainer.getChildren().clear();

            // A. NÚT "TẤT CẢ"
            Button btnAll = createGroupButton("Tất cả thiết bị", null);
            groupContainer.getChildren().add(btnAll);
            groupContainer.getChildren().add(new Separator());

            // B. DANH SÁCH NHÓM TỪ DB
            Map<Integer, String> groups = groupService.getAllGroups();
            for (Map.Entry<Integer, String> entry : groups.entrySet()) {
                Button btn = createGroupButton(entry.getValue(), entry.getKey());
                groupContainer.getChildren().add(btn);
            }

            // C. NÚT TẠO KEY (Chỉ hiện khi chọn 1 nhóm cụ thể)
            if (selectedGroupId != null) {
                Separator sep = new Separator();
                Button btnGenKey = new Button("🔑 Tạo Key cho nhóm này");
                btnGenKey.setMaxWidth(Double.MAX_VALUE);
                btnGenKey.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
                btnGenKey.setOnAction(e -> onGenerateKeyForSelectedGroup());
                groupContainer.getChildren().addAll(sep, btnGenKey);
            }
        });
    }

    private Button createGroupButton(String name, Integer groupId) {
        Button btn = new Button((groupId == null ? "🏠 " : "• ") + name);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setCursor(javafx.scene.Cursor.HAND);
        btn.setPadding(new javafx.geometry.Insets(8));

        // Tô màu nếu đang chọn
        boolean isActive = (selectedGroupId == null && groupId == null) ||
                (selectedGroupId != null && selectedGroupId.equals(groupId));

        if (isActive) {
            btn.setStyle("-fx-alignment: CENTER_LEFT; -fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        } else {
            btn.setStyle("-fx-alignment: CENTER_LEFT; -fx-background-color: transparent; -fx-text-fill: black;");
        }

        // Sự kiện Click trái: Lọc máy
        btn.setOnAction(e -> {
            this.selectedGroupId = groupId;
            refreshGroupList(); // Update màu nút
            loadClientList();   // Load lại máy
        });

        // Sự kiện Chuột phải: Xóa nhóm
        if (groupId != null) {
            ContextMenu ctx = new ContextMenu();
            MenuItem delItem = new MenuItem("🗑 Xóa nhóm này (Revoke All)");
            delItem.setStyle("-fx-text-fill: red;");
            delItem.setOnAction(e -> {
                confirmAction("CẢNH BÁO: Xóa nhóm sẽ xóa và ngắt kết nối TẤT CẢ máy trong nhóm.\nTiếp tục?", () -> {
                    if (groupService.deleteGroup(groupId)) {
                        if (selectedGroupId != null && selectedGroupId.equals(groupId)) selectedGroupId = null;
                        refreshGroupList();
                        loadClientList();
                        showInfo("Đã xóa nhóm thành công!");
                    } else showError("Xóa nhóm thất bại.");
                });
            });
            ctx.getItems().add(delItem);
            btn.setContextMenu(ctx);
        }

        return btn;
    }

    // =========================================================================
    // 2. HIỂN THỊ DANH SÁCH MÁY (GRID VIEW)
    // =========================================================================

    private void loadClientList() {
        Platform.runLater(() -> {
            // 1. Dọn dẹp giao diện cũ (Cả Grid giữa và List trái)
            myGrid.getChildren().clear();
            deviceListContainer.getChildren().clear(); // <--- QUAN TRỌNG: Xóa list bên trái cũ

            clientViews.clear();
            clientContainers.clear();

            // 2. Lấy dữ liệu từ Database
            List<ClientDevice> devices;
            if (selectedGroupId == null) devices = deviceDAO.getAllClients();
            else devices = deviceDAO.getClientsByGroup(selectedGroupId);

            if (devices.isEmpty()) {
                myGrid.add(new Label("Không có thiết bị."), 0, 0);
                deviceListContainer.getChildren().add(new Label("(Trống)"));
                return;
            }

            int index = 0;
            for (ClientDevice dev : devices) {
                // A. VẼ LÊN GRID (Ở GIỮA)
                VBox card = createClientCardUI(dev);
                myGrid.add(card, index % 4, index / 4);
                index++;

                // B. VẼ LÊN SIDEBAR (BÊN TRÁI) - CHỈ MÁY ONLINE
                // Kiểm tra xem máy có đang kết nối không
                boolean isOnline = sessionManager.get(dev.getClientId()) != null;

                if (isOnline) {
                    Button btnSidebar = new Button("● " + dev.getClientName());
                    btnSidebar.setMaxWidth(Double.MAX_VALUE);
                    // Style: Chữ xanh lá, căn trái, chuột dạng bàn tay
                    btnSidebar.setStyle("-fx-background-color: transparent; -fx-alignment: CENTER_LEFT; -fx-text-fill: #2e7d32; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: 12px;");

                    // Bấm vào tên bên trái thì cũng chọn máy đó (giống bấm vào Grid)
                    btnSidebar.setOnAction(e -> selectClient(dev.getClientId()));

                    deviceListContainer.getChildren().add(btnSidebar);
                }
            }

            // Nếu không có máy nào online
            if (deviceListContainer.getChildren().isEmpty()) {
                Label lbl = new Label("(Không có máy online)");
                lbl.setStyle("-fx-text-fill: #999; -fx-padding: 5; -fx-font-size: 11px;");
                deviceListContainer.getChildren().add(lbl);
            }
        });
    }

    private VBox createClientCardUI(ClientDevice dev) {
        // Thumbnail
        ImageView iv = new ImageView(); // Cần set ảnh mặc định nếu muốn
        iv.setFitWidth(140);
        iv.setPreserveRatio(true);
        clientViews.put(dev.getClientId(), iv);

        // Thông tin
        Label lblName = new Label(dev.getClientName());
        lblName.setStyle("-fx-font-weight: bold;");
        Label lblIp = new Label(dev.getCurrentIp());

        // Trạng thái Online (Check qua SessionManager)
        boolean isOnline = sessionManager.get(dev.getClientId()) != null;
        Label lblStatus = new Label(isOnline ? "● Online" : "○ Offline");
        lblStatus.setStyle("-fx-text-fill: " + (isOnline ? "green" : "red") + "; -fx-font-size: 10px;");

        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setPrefSize(160, 140);
        String borderColor = isOnline ? "#4caf50" : "#ef5350";
        card.setStyle("-fx-padding: 10; -fx-background-color: white; -fx-border-color: " + borderColor + "; -fx-border-radius: 5; -fx-cursor: hand;");
        card.getChildren().addAll(iv, lblName, lblIp, lblStatus);

        // Click trái: Chọn máy
        card.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) selectClient(dev.getClientId());
        });

        // Chuột phải: Menu chức năng
        ContextMenu ctx = new ContextMenu();

        MenuItem itemRemote = new MenuItem("Điều khiển");
        itemRemote.setOnAction(e -> { selectClient(dev.getClientId()); onControlClick(); });

        MenuItem itemRevoke = new MenuItem("Thu hồi (Xóa)");
        itemRevoke.setStyle("-fx-text-fill: red; font-weight: bold;");
        itemRevoke.setOnAction(e -> {
            confirmAction("Xác nhận xóa thiết bị " + dev.getClientName() + "?", () -> {
                if (groupService.revokeClient(dev.getClientId())) {
                    loadClientList();
                    if (selectedClientId != null && selectedClientId == dev.getClientId()) resetDetailPanel();
                } else showError("Lỗi xóa thiết bị.");
            });
        });

        ctx.getItems().addAll(itemRemote, new SeparatorMenuItem(), itemRevoke);
        card.setOnContextMenuRequested(e -> ctx.show(card, e.getScreenX(), e.getScreenY()));

        clientContainers.put(dev.getClientId(), card);
        return card;
    }

    // =========================================================================
    // 3. XỬ LÝ SỰ KIỆN CLICK VÀ CẬP NHẬT CHI TIẾT
    // =========================================================================

    private void selectClient(int clientId) {
        // Reset style máy cũ
        if (selectedClientId != null && clientContainers.containsKey(selectedClientId)) {
            // Lấy lại trạng thái online để trả về màu border đúng
            boolean online = sessionManager.get(selectedClientId) != null;
            String color = online ? "#4caf50" : "#ef5350";
            clientContainers.get(selectedClientId).setStyle("-fx-padding: 10; -fx-background-color: white; -fx-border-color: " + color + "; -fx-border-radius: 5; -fx-cursor: hand;");
        }

        this.selectedClientId = clientId;

        // Highlight máy mới
        if (clientContainers.containsKey(clientId)) {
            clientContainers.get(clientId).setStyle("-fx-padding: 9; -fx-background-color: #e3f2fd; -fx-border-color: #2196F3; -fx-border-width: 2; -fx-cursor: hand;");
        }

        // Cập nhật Panel bên phải
        ClientSession session = sessionManager.get(clientId);
        Optional<ClientDevice> devOpt = deviceDAO.getClientById(clientId);
        if (devOpt.isPresent()) {
            ClientDevice dev = devOpt.get();
            osLabel.setText("OS: " + (dev.getOs() != null ? dev.getOs() : "Unknown"));
            cpuModelLabel.setText("CPU: " + (dev.getCpuInfo() != null ? dev.getCpuInfo() : "Unknown"));
            double ramGb = dev.getRamTotal() / (1024.0 * 1024 * 1024);
            String ramText = (dev.getRamTotal() > 0) ? String.format("%.1f GB", ramGb) : "Unknown";
            ramTotalLabel.setText("RAM: " + ramText);
            diskTotalLabel.setText("DISK: " + (dev.getDiskTotalGb() >0 ? dev.getDiskTotalGb() + " GB" : "Unknown"));
        }
        if (session != null) {
            nameLabel.setText("Name: " + session.getHostname());
            ipLabel.setText("IP: " + session.getIpAddress());
            macLabel.setText("MAC: " + session.getMacAddress());
        } else {
            // Nếu offline thì lấy từ DB (cần thêm hàm getById trong DAO, tạm thời để trống)
            nameLabel.setText("Name: (Offline)");
        }
        resetDynamicLabels();
    }

    private void resetDetailPanel() {
        selectedClientId = null;
        nameLabel.setText("Name: --"); ipLabel.setText("IP: --"); macLabel.setText("MAC: --");
        resetDynamicLabels();
    }

    private void resetDynamicLabels() {
        cpuLabel.setText("CPU: --"); ramLabel.setText("RAM: --");
        diskLabel.setText("DISK: --"); networkLabel.setText("NET: --");
    }

    // =========================================================================
    // 4. NHẬN DỮ LIỆU REAL-TIME (Screen, Heartbeat)
    // =========================================================================

    @Override
    public void onScreenFrameReceived(int clientId, byte[] imageBytes) {
        if (imageBytes == null) return;
        if (activeScreens.containsKey(clientId)) activeScreens.get(clientId).updateFrame(imageBytes);

        Platform.runLater(() -> {
            // Chỉ cập nhật Thumbnail nếu máy đó đang hiển thị trên Grid
            if (clientViews.containsKey(clientId)) {
                clientViews.get(clientId).setImage(new Image(new ByteArrayInputStream(imageBytes)));
            }
        });
    }

    @Override
    public void onHeartbeatReceived(int clientId, HeartbeatModel hb) {
        if (selectedClientId != null && selectedClientId == clientId) {
            Platform.runLater(() -> {
                cpuLabel.setText("CPU: " + hb.getCpuUsage() + "%");
                ramLabel.setText("RAM: " + hb.getRamUsage() + "%");
                diskLabel.setText("DISK: " + hb.getDiskFreePercent() + "%");
                networkLabel.setText("NET: ↑" + hb.getNetSentRateKb() + " / ↓" + hb.getNetRecvRateKb() + " KB/s");
            });
        }
    }

    @Override
    public void onProcessListReceived(int clientId, List<ProcessInfo> processes) {
        if (activeProcessWindows.containsKey(clientId)) {
            activeProcessWindows.get(clientId).updateTable(processes);
        }
    }

    // =========================================================================
    // 5. CÁC NÚT CHỨC NĂNG (FXML ACTIONS)
    // =========================================================================

    @FXML public void onAddGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Tạo Nhóm");
        dialog.setHeaderText("Thêm nhóm mới");
        dialog.setContentText("Tên nhóm:");
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                if (groupService.insertGroup(name.trim()) > 0) {
                    refreshGroupList();
                    showInfo("Tạo nhóm thành công!");
                } else showError("Lỗi tạo nhóm.");
            }
        });
    }

    @FXML public void onControlClick() { openWindow("/screen_view.fxml", "Remote View", (id, l) -> commandService.startScreenStream(id, true)); }
    @FXML public void onProcessClick() { openWindow("/process_view.fxml", "Process Manager", (id, l) -> commandService.requestProcessList(id)); }
    @FXML public void onFileExplorerClick() { openWindow("/file_manager.fxml", "File Explorer", (id, l) -> commandService.requestFileTree(id, "")); }

    @FXML public void onSendFileClick() {
        if (selectedClientId == null) return;
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        File f = fc.showOpenDialog(null);
        if (f != null) commandService.sendDownloadRequest(selectedClientId, f);
    }

    @FXML public void onShutdownClick() { if (selectedClientId != null) confirmAction("Tắt máy?", () -> commandService.sendShutdown(selectedClientId)); }
    @FXML public void onSleepClick() { if (selectedClientId != null) commandService.sendSleep(selectedClientId); }

    private void onGenerateKeyForSelectedGroup() {
        if (selectedGroupId == null) return;
        String name = groupService.getAllGroups().get(selectedGroupId);
        String key = groupService.createGroupKey(selectedGroupId, name, 30);
        if (key != null) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Key Created");
            a.setHeaderText("Tạo Key thành công cho: " + name);
            a.setContentText("File: " + name + ".grpkey\nKey: " + key);
            a.showAndWait();
        }
    }

    // Helper mở cửa sổ con
    private void openWindow(String fxml, String title, java.util.function.BiConsumer<Integer, FXMLLoader> onLoaded) {
        if (selectedClientId == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            Parent root = loader.load();

            // Xử lý init data cho controller con (cần ép kiểu tùy controller)
            if (fxml.contains("screen")) {
                ScreenController sc = loader.getController();
                sc.initData(selectedClientId, "Client " + selectedClientId);
                activeScreens.put(selectedClientId, sc);
            } else if (fxml.contains("process")) {
                ProcessController pc = loader.getController();
                pc.initData(selectedClientId, commandService);
                activeProcessWindows.put(selectedClientId, pc);
            } else if (fxml.contains("file")) {
                FileExplorerController fec = loader.getController();
                fec.initData(selectedClientId, commandService);
                commandService.addFileExplorer(selectedClientId, fec);
                activeFileExplorers.put(selectedClientId, fec);
            }

            Stage stage = new Stage();
            stage.setTitle(title + " - " + selectedClientId);
            stage.setScene(new Scene(root));
            stage.show();

            // Callback để gửi lệnh start stream/request list
            if (onLoaded != null) onLoaded.accept(selectedClientId, loader);

        } catch (IOException e) { showError("Lỗi mở cửa sổ: " + e.getMessage()); }
    }

    private void showInfo(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, msg).show()); }
    private void showError(String msg) { Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg).show()); }
    private void confirmAction(String msg, Runnable action) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO);
        a.showAndWait().ifPresent(r -> { if (r == ButtonType.YES) action.run(); });
    }
}