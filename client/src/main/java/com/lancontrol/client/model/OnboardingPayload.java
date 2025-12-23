package com.lancontrol.client.model;

public class OnboardingPayload {
    private String onboardingKey;
    private String macAddress;
    private String clientName;
    private String os;
    private String cpuInfo;
    private long ramTotal;
    private int diskTotalGb;
    private String currentIp;
    public OnboardingPayload(String onboardingKey, String macAddress, String clientName, String os, String cpuInfo, long ramTotal, int diskTotalGb) {
        this.onboardingKey = onboardingKey;
        this.macAddress = macAddress;
        this.clientName = clientName;
        this.os = os;
        this.cpuInfo = cpuInfo;
        this.ramTotal = ramTotal;
        this.diskTotalGb = diskTotalGb;
    }
    public OnboardingPayload() {
    }
    public void setOnboardingKey(String onboardingKey) {
        this.onboardingKey = onboardingKey;
    }
    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }
    public void setClientName(String clientName) {
        this.clientName = clientName;
    }
    public void setOs(String os) {
        this.os = os;
    }
    public void setCpuInfo(String cpuInfo) {
        this.cpuInfo = cpuInfo;
    }
    public void setRamTotal(long ramTotal) {
        this.ramTotal = ramTotal;
    }
    public void setDiskTotalGb(int diskTotalGb) {
        this.diskTotalGb = diskTotalGb;
    }

    public String getCurrentIp() {
        return currentIp;
    }

    public void setCurrentIp(String currentIp) {
        this.currentIp = currentIp;
    }

    public OnboardingPayload(String onboardingKey, String macAddress, String clientName, String os, String cpuInfo, long ramTotal, int diskTotalGb, String currentIp) {
        this.onboardingKey = onboardingKey;
        this.macAddress = macAddress;
        this.clientName = clientName;
        this.os = os;
        this.cpuInfo = cpuInfo;
        this.ramTotal = ramTotal;
        this.diskTotalGb = diskTotalGb;
        this.currentIp = currentIp;
    }
    public String getOnboardingKey() { return onboardingKey; }
    public String getMacAddress() { return macAddress; }
    public String getClientName() { return clientName; }
    public String getOs() { return os; }
    public String getCpuInfo() { return cpuInfo; }
    public long getRamTotal() { return ramTotal; }
    public int getDiskTotalGb() { return diskTotalGb; }
}