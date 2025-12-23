package com.lancontrol.client.model; // Hoặc package model chung của bạn

public class ProcessInfo {
    private int pid;
    private String name;
    private double cpuUsage;    // Đổi tên cho khớp logic Server
    private double memoryUsage; // Đổi tên từ memoryUsageMb -> memoryUsage

    public ProcessInfo() {}

    // Getter/Setter phải đặt đúng chuẩn CamelCase để JavaFX tìm thấy
    public int getPid() { return pid; }
    public void setPid(int pid) { this.pid = pid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }

    public double getMemoryUsage() { return memoryUsage; }
    public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }
}