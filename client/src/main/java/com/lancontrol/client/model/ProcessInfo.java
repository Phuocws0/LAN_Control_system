package com.lancontrol.client.model;

public class ProcessInfo {
    private int pid;
    private String name;
    private double memoryUsageMb;

    public ProcessInfo() {
    }

    public ProcessInfo(int pid, String name, double memoryUsageMb) {
        this.pid = pid;
        this.name = name;
        this.memoryUsageMb = memoryUsageMb;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMemoryUsageMb(double memoryUsageMb) {
        this.memoryUsageMb = memoryUsageMb;
    }

    public int getPid() { return pid; }
    public String getName() { return name; }
    public double getMemoryUsageMb() { return memoryUsageMb; }
}