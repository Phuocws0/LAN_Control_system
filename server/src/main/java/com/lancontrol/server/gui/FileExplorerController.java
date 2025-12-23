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

/**
 * Controller xử lý giao diện duyệt file từ xa.
 */
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

    @FXML
    public void initialize() {
        // 1. Thiết lập các cột hiển thị [cite: 900-905]
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        // Cột loại: Hiển thị "Thư mục" hoặc "Tệp"
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

        // Cột kích thước: Chuyển đổi bytes sang định dạng dễ đọc (KB/MB)
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

        // Cột ngày: Định dạng timestamp thành chuỗi ngày tháng
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

        // 2. Xử lý sự kiện Double-Click để vào thư mục
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

    /**
     * Khởi tạo dữ liệu khi mở cửa sổ.
     */
    public void initData(int clientId, CommandService commandService) {
        this.clientId = clientId;
        this.commandService = commandService;
        // Mặc định yêu cầu danh sách các ổ đĩa (path trống) [cite: 714]
        requestPath("");
    }

    /**
     * Gửi lệnh yêu cầu lấy danh sách file tại đường dẫn chỉ định.
     */
    private void requestPath(String path) {
        statusLabel.setText("Đang tải dữ liệu...");
        commandService.sendSecure(clientId, "GET_FILE_TREE", path);
        pathField.setText(path);
    }

    /**
     * Cập nhật bảng khi nhận được phản hồi từ Client.
     */
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

        // Xử lý lấy đường dẫn thư mục cha
        java.io.File file = new java.io.File(currentPath);
        String parent = file.getParent();

        if (parent != null) {
            requestPath(parent);
        } else {
            requestPath(""); // Quay lại danh sách ổ đĩa
        }
    }

    @FXML
    public void onDownloadToServer() {
        FileNode selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (selected.isDirectory()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Không thể tải trực tiếp thư mục. Hãy vào trong để chọn tệp.");
                alert.show();
            } else {
                statusLabel.setText("Đang yêu cầu tải lên: " + selected.getName());
                // Gửi lệnh yêu cầu Client Upload tệp này lên Server [cite: 104, 849]
                commandService.sendSecure(clientId, "REQ_UPLOAD_FILE", selected.getPath());
            }
        }
    }

    // Tiện ích định dạng kích thước file
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