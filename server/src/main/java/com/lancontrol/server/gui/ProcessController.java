package com.lancontrol.server.gui;

import com.lancontrol.server.model.ProcessInfo;
import com.lancontrol.server.service.CommandService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import java.util.List;

public class ProcessController {

    @FXML private TableView<ProcessInfo> processTable;
    @FXML private TableColumn<ProcessInfo, Integer> pidCol;
    @FXML private TableColumn<ProcessInfo, String> nameCol;
    @FXML private TableColumn<ProcessInfo, Double> cpuCol;
    @FXML private TableColumn<ProcessInfo, Double> memCol;

    private int clientId;
    private CommandService commandService;
    private Stage stage;
    private final ObservableList<ProcessInfo> masterData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        pidCol.setCellValueFactory(new PropertyValueFactory<>("pid"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        cpuCol.setCellValueFactory(new PropertyValueFactory<>("cpuUsage"));   // Phải khớp tên biến ở Client
        memCol.setCellValueFactory(new PropertyValueFactory<>("memoryUsage")); // Phải khớp tên biến ở Client

        processTable.setItems(masterData);
    }

    /**
     * Khởi tạo dữ liệu ban đầu cho cửa sổ (Được gọi từ ServerController)
     */
    public void initData(int clientId, CommandService commandService) {
        this.clientId = clientId;
        this.commandService = commandService;
    }

    /**
     * Cập nhật danh sách tiến trình từ dữ liệu Client gửi về
     */
    public void updateTable(List<ProcessInfo> processes) {
        if (processes == null) return;

        Platform.runLater(() -> {
            masterData.clear(); // Xóa dữ liệu cũ
            masterData.addAll(processes); // Thêm dữ liệu mới
            processTable.refresh(); // Ép bảng cập nhật giao diện
            System.out.println(">> [UI] Đã hiển thị " + processes.size() + " tiến trình lên bảng.");
        });
    }

    @FXML
    public void onRefresh() {
        // Gửi yêu cầu lấy danh sách mới
        if (commandService != null) {
            commandService.requestProcessList(clientId);
        }
    }

    @FXML
    public void onKillProcess() {
        ProcessInfo selected = processTable.getSelectionModel().getSelectedItem();
        if (selected != null && commandService != null) {
            // Gửi lệnh tiêu diệt tiến trình dựa trên PID
            commandService.killProcess(clientId, selected.getPid());

            // Xóa tạm thời khỏi bảng để tạo cảm giác phản hồi nhanh
            masterData.remove(selected);
        }
    }

    public void setStage(Stage stage) { this.stage = stage; }
    public Stage getStage() { return stage; }
}