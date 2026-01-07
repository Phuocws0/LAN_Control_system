package com.lancontrol.client.service;

import com.lancontrol.client.model.FileNode;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileExplorerService {

    public List<FileNode> listFiles(String path) {
        List<FileNode> nodes = new ArrayList<>();
        String cleanPath = (path == null) ? "" : path.replace("\"", "").trim();
        File[] files;
        if (cleanPath.isEmpty()) {
            files = File.listRoots();
        } else {
            File dir = new File(cleanPath);
            if (dir.exists() && dir.isDirectory()) {
                files = dir.listFiles();
            } else {
                System.err.println(">> [Client] Thư mục không hợp lệ: " + cleanPath);
                return nodes;
            }
        }

        if (files != null) {
            for (File f : files) {
                String name = (f.getName() == null || f.getName().isEmpty()) ? f.getPath() : f.getName();
                nodes.add(new com.lancontrol.client.model.FileNode(
                        name,
                        f.getAbsolutePath(),
                        f.isDirectory(),
                        f.isDirectory() ? 0 : f.length(),
                        f.lastModified()
                ));
            }
        }
        return nodes;
    }
}