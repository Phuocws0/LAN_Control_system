package com.lancontrol.server.model;

import java.util.ArrayList;
import java.util.List;

public class FileNode {
    private String name;
    private String path;
    private boolean isDirectory;
    private long size;
    private long lastModified;

    public FileNode() {}

    public FileNode(String name, String path, boolean isDirectory, long size, long lastModified) {
        this.name = name;
        this.path = path;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
    }
    public String getName() { return name; }
    public String getPath() { return path; }
    public boolean isDirectory() { return isDirectory; }
    public long getSize() { return size; }
    public long getLastModified() { return lastModified; }
}