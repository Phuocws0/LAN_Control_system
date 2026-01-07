package com.lancontrol.server.gui;

import com.lancontrol.server.model.FileNode;
import com.lancontrol.server.service.CommandService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class FileExplorerController {

    @FXML private TableView<FileNode> fileTable;
    @FXML private TableColumn<FileNode, String> nameCol;
    @FXML private TableColumn<FileNode, String> typeCol;
    @FXML private TableColumn<FileNode, String> sizeCol;
    @FXML private TableColumn<FileNode, String> dateCol;

    @FXML private TextField pathField;
    @FXML private Label statusLabel;

    private int clientId;
    private CommandService commandService;
    private Stage stage;
    private final ObservableList<FileNode> masterData = FXCollections.observableArrayList();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    // khoi tao bang va cac cot
    @FXML
    public void initialize() {
        // cot ten
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(getTableRow().getItem().isDirectory() ? "Thư mục" : "Tệp");
                }
            }
        });
        // cot kich thuoc
        sizeCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    FileNode node = getTableRow().getItem();
                    if (node.isDirectory()) {
                        setText("--");
                    } else {
                        setText(formatSize(node.getSize()));
                    }
                }
            }
        });
        // dinh dang cot ngay thang
        dateCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(dateFormat.format(new Date(getTableRow().getItem().getLastModified())));
                }
            }
        });

        fileTable.setItems(masterData);

        // xu ly su kien double-click de vao thu muc
        fileTable.setRowFactory(tv -> {
            TableRow<FileNode> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    FileNode node = row.getItem();
                    if (node.isDirectory()) {
                        requestPath(node.getPath());
                    }
                }
            });
            return row;
        });
    }

    // Khoi tao du lieu cho File Explorer
    public void initData(int clientId, CommandService commandService) {
        this.clientId = clientId;
        this.commandService = commandService;
        // Mặc định yêu cầu danh sách các ổ đĩa (path trống) [cite: 714]
        requestPath("");
    }

    // gui yeu cau duong dan den client
    private void requestPath(String path) {
        statusLabel.setText("Đang tải dữ liệu...");
        commandService.sendSecure(clientId, "GET_FILE_TREE", path);
        pathField.setText(path);
    }

   // cap nhat file explorer voi danh sach file moi
    public void updateBrowser(List<FileNode> nodes) {
        if (nodes == null) return;
        Platform.runLater(() -> {
            masterData.setAll(nodes);
            statusLabel.setText("Đã tìm thấy " + nodes.size() + " mục.");
            fileTable.refresh();
        });
    }

    @FXML
    public void onGoToPath() {
        requestPath(pathField.getText().trim());
    }

    @FXML
    public void onBack() {
        String currentPath = pathField.getText();
        if (currentPath == null || currentPath.isEmpty()) return;

       // lay duong dan cha
        java.io.File file = new java.io.File(currentPath);
        String parent = file.getParent();

        if (parent != null) {
            requestPath(parent);
        } else {
            requestPath(""); // ve danh sach o dia
        }
    }
    // xu ly su kien download file ve server
    @FXML
    public void onDownloadToServer() {
        FileNode selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (selected.isDirectory()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Không thể tải trực tiếp thư mục. Hãy vào trong để chọn tệp.");
                alert.show();
            } else {
                statusLabel.setText("Đang yêu cầu tải lên: " + selected.getName());
                commandService.sendSecure(clientId, "REQ_UPLOAD_FILE", selected.getPath());
            }
        }
    }
    // dinh dang kich thuoc file
    private String formatSize(long v) {
        if (v < 1024) return v + " B";
        int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
        return String.format("%.1f %sB", (double)v / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
    public void updateProgress(double percent) {
        Platform.runLater(() -> {
            statusLabel.setText(String.format("Đang tải: %.1f%%", percent));
        });
    }
    public void setStage(Stage stage) { this.stage = stage; }
    public Stage getStage() { return stage; }
}