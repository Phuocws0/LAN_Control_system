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
        cpuCol.setCellValueFactory(new PropertyValueFactory<>("cpuUsage"));
        memCol.setCellValueFactory(new PropertyValueFactory<>("memoryUsage"));

        processTable.setItems(masterData);
    }

    // khoi tao du lieu
    public void initData(int clientId, CommandService commandService) {
        this.clientId = clientId;
        this.commandService = commandService;
    }
    // cap nhat du lieu len bang
    public void updateTable(List<ProcessInfo> processes) {
        if (processes == null) return;

        Platform.runLater(() -> {
            masterData.clear();
            masterData.addAll(processes);
            processTable.refresh();
            System.out.println(">> [UI] Đã hiển thị " + processes.size() + " tiến trình lên bảng.");
        });
    }

    @FXML
    public void onRefresh() {
        if (commandService != null) {
            commandService.requestProcessList(clientId);
        }
    }

    @FXML
    public void onKillProcess() {
        ProcessInfo selected = processTable.getSelectionModel().getSelectedItem();
        if (selected != null && commandService != null) {
            commandService.killProcess(clientId, selected.getPid());
            masterData.remove(selected);
        }
    }

    public void setStage(Stage stage) { this.stage = stage; }
    public Stage getStage() { return stage; }
}