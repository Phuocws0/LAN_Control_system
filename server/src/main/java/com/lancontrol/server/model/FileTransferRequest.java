package com.lancontrol.server.model; // Ở Client sửa thành com.lancontrol.client.model

public class FileTransferRequest {
    private String fileId;
    private String fileName;
    private long fileSize;

    public FileTransferRequest(String fileId, String fileName, long fileSize) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public FileTransferRequest() {
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileId() { return fileId; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
}