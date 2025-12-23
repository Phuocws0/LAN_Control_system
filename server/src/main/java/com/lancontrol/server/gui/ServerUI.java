package com.lancontrol.server.gui;

import com.lancontrol.server.network.ClientHandler;
import com.lancontrol.server.network.FileServer;
import com.lancontrol.server.network.SessionManager;
import com.lancontrol.server.service.AuthService;
import com.lancontrol.server.service.CommandService;
import com.lancontrol.server.service.GroupService;
import com.lancontrol.server.service.HeartbeatService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

public class ServerUI extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // --- 1. KHỞI TẠO BACKEND (Thứ tự phụ thuộc để tránh lỗi biên dịch) ---
        SessionManager sessionManager = new SessionManager();
        AuthService authService = new AuthService();

        // Khởi tạo dịch vụ lắng nghe nhịp tim (Heartbeat)
        HeartbeatService heartbeatService = new HeartbeatService();

        // Khởi tạo và chạy FileServer (Cổng 9998)
        FileServer fileServer = new FileServer(authService);
        fileServer.start();

        // Khởi tạo CommandService
        CommandService commandService = new CommandService(sessionManager, fileServer);

        // Khởi tạo GroupService với đầy đủ tham số
        GroupService groupService = new GroupService(commandService, sessionManager);

        // --- 2. MỞ CỔNG 9999 (Sửa lỗi Client báo "Mất kết nối") ---
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(9999)) {
                System.out.println(">> [Server] Đang lắng nghe cổng lệnh 9999...");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    // Mỗi khi Client kết nối, ClientHandler sẽ xử lý trong luồng riêng
                    new Thread(new ClientHandler(
                            clientSocket, sessionManager, authService, heartbeatService, commandService
                    )).start();
                }
            } catch (Exception e) {
                System.err.println(">> Lỗi ServerSocket 9999: " + e.getMessage());
            }
        }).start();

        // --- 3. KHỞI TẠO GIAO DIỆN (Sửa lỗi Location is not set) ---
        // Lưu ý: Đảm bảo file server_main.fxml nằm trong thư mục resources
        String fxmlPath = "/com/lancontrol/server/gui/server_main.fxml";
        URL fxmlLocation = getClass().getResource(fxmlPath);

        if (fxmlLocation == null) {
            // Nếu không tìm thấy ở đường dẫn package, thử tìm ở root của resources
            fxmlLocation = getClass().getResource("/server_main.fxml");
            if (fxmlLocation == null) {
                throw new RuntimeException("!!! LỖI: Không tìm thấy file FXML tại: " + fxmlPath);
            }
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);

        // QUAN TRỌNG: Khởi tạo Controller với 4 THAM SỐ (cs, sm, gs, hs)
        ServerController controller = new ServerController(
                commandService,
                sessionManager,
                groupService,
                heartbeatService
        );

        // Tiêm controller vào loader TRƯỚC khi gọi load()
        loader.setController(controller);

        Parent root = loader.load();
        primaryStage.setTitle("Hệ thống Quản trị LAN");
        primaryStage.setScene(new Scene(root));

        // Đảm bảo tắt toàn bộ tiến trình Java khi đóng cửa sổ
        primaryStage.setOnCloseRequest(e -> System.exit(0));
        primaryStage.show();

        System.out.println(">> [UI] Dashboard đã sẵn sàng điều khiển.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}