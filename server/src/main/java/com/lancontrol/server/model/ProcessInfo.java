package com.lancontrol.server.model;

public class ProcessInfo {
    private int pid;
    private String name;
    private double memoryUsageMb;
    public int getPid() { return pid; }
    public String getName() { return name; }
    public double getMemoryUsageMb() { return memoryUsageMb; }
}