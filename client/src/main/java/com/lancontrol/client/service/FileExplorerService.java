package com.lancontrol.client.service;

import com.lancontrol.client.model.FileNode;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileExplorerService {
    /**
     * Liệt kê danh sách file/thư mục tại một đường dẫn.
     * Nếu path là null hoặc trống, sẽ liệt kê các ổ đĩa (C:\, D:\, /...)
     */
    public List<FileNode> listFiles(String path) {
        List<FileNode> nodes = new ArrayList<>();
        File[] files;

        if (path == null || path.isEmpty()) {
            // Liệt kê các gốc hệ thống (Drives)
            files = File.listRoots();
        } else {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                files = dir.listFiles();
            } else {
                return nodes;
            }
        }

        if (files != null) {
            for (File f : files) {
                // Tạo node thông tin (Lấy tên root nếu f.getName() trống cho các ổ đĩa)
                String name = (f.getName().isEmpty()) ? f.getPath() : f.getName();
                nodes.add(new FileNode(
                        name,
                        f.getAbsolutePath(),
                        f.isDirectory(),
                        f.length(),
                        f.lastModified()
                ));
            }
        }
        return nodes;
    }
}