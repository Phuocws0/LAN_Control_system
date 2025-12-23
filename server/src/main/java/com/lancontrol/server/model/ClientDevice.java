package com.lancontrol.server.model;

import java.sql.Timestamp;

public class ClientDevice {
    private int clientId;
    private String macAddress;
    private int groupId;
    private String clientName;
    private String currentIp;
    private String os;
    private String cpuInfo;
    private long ramTotal;
    private int diskTotalGb;
    private Timestamp lastSeen;
    private boolean isOnline;
    public ClientDevice() {}
    public int getClientId() { return clientId; }
    public void setClientId(int clientId) { this.clientId = clientId; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getCurrentIp() { return currentIp; }
    public void setCurrentIp(String currentIp) { this.currentIp = currentIp; }

    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }

    public String getCpuInfo() { return cpuInfo; }
    public void setCpuInfo(String cpuInfo) { this.cpuInfo = cpuInfo; }

    public long getRamTotal() { return ramTotal; }
    public void setRamTotal(long ramTotal) { this.ramTotal = ramTotal; }

    public int getDiskTotalGb() { return diskTotalGb; }
    public void setDiskTotalGb(int diskTotalGb) { this.diskTotalGb = diskTotalGb; }

    public Timestamp getLastSeen() { return lastSeen; }
    public void setLastSeen(Timestamp lastSeen) { this.lastSeen = lastSeen; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }
}