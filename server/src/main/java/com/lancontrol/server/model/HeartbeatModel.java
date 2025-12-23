package com.lancontrol.server.model;

public class HeartbeatModel {
    private short cpuUsage;
    private short ramUsage;
    private int diskWriteRateKb;
    private int diskReadRateKb;
    private short diskFreePercent;
    private int netSentRateKb;
    private int netRecvRateKb;

    public short getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(short cpuUsage) { this.cpuUsage = cpuUsage; }
    public short getRamUsage() { return ramUsage; }
    public void setRamUsage(short ramUsage) { this.ramUsage = ramUsage; }
    public int getDiskWriteRateKb() { return diskWriteRateKb; }
    public void setDiskWriteRateKb(int diskWriteRateKb) { this.diskWriteRateKb = diskWriteRateKb; }
    public int getDiskReadRateKb() { return diskReadRateKb; }
    public void setDiskReadRateKb(int diskReadRateKb) { this.diskReadRateKb = diskReadRateKb; }
    public short getDiskFreePercent() { return diskFreePercent; }
    public void setDiskFreePercent(short diskFreePercent) { this.diskFreePercent = diskFreePercent; }
    public int getNetSentRateKb() { return netSentRateKb; }
    public void setNetSentRateKb(int netSentRateKb) { this.netSentRateKb = netSentRateKb; }
    public int getNetRecvRateKb() { return netRecvRateKb; }
    public void setNetRecvRateKb(int netRecvRateKb) { this.netRecvRateKb = netRecvRateKb; }
}