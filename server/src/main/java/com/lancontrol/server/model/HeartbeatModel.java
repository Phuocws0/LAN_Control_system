package com.lancontrol.server.model;

import java.io.Serializable;

public class HeartbeatModel implements Serializable {
    private short cpuUsage;
    private short ramUsage;
    private short diskFreePercent;
    private int diskReadRateKb;
    private int diskWriteRateKb;
    private int netRecvRateKb;
    private int netSentRateKb;

    public short getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(short cpuUsage) { this.cpuUsage = cpuUsage; }

    public short getRamUsage() { return ramUsage; }
    public void setRamUsage(short ramUsage) { this.ramUsage = ramUsage; }

    public short getDiskFreePercent() { return diskFreePercent; }
    public void setDiskFreePercent(short diskFreePercent) { this.diskFreePercent = diskFreePercent; }

    public int getDiskReadRateKb() { return diskReadRateKb; }
    public void setDiskReadRateKb(int diskReadRateKb) { this.diskReadRateKb = diskReadRateKb; }

    public int getDiskWriteRateKb() { return diskWriteRateKb; }
    public void setDiskWriteRateKb(int diskWriteRateKb) { this.diskWriteRateKb = diskWriteRateKb; }

    public int getNetRecvRateKb() { return netRecvRateKb; }
    public void setNetRecvRateKb(int netRecvRateKb) { this.netRecvRateKb = netRecvRateKb; }

    public int getNetSentRateKb() { return netSentRateKb; }
    public void setNetSentRateKb(int netSentRateKb) { this.netSentRateKb = netSentRateKb; }
}