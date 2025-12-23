package com.lancontrol.client.model;

public class NetworkPacket {
    private String command;
    private String token;
    private String payloadJson;
    private long timestamp;
    private String hmac;
    private long sequenceNumber;

    public NetworkPacket() {}
    public NetworkPacket(String command, String token, String payloadJson, long timestamp) {
        this.command = command;
        this.token = token;
        this.payloadJson = payloadJson;
        this.timestamp = timestamp;
    }
    public String getCommand() {
        return command;
    }
    public String getToken() {
        return token;
    }
    public void setCommand(String command) {
        this.command = command;
    }
    public void setToken(String token) {
        this.token = token;
    }
    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    public String getPayloadJson() {
        return payloadJson;
    }
    public long getTimestamp() {
        return timestamp;
    }
    public String getHmac() { return hmac; }
    public void setHmac(String hmac) { this.hmac = hmac; }
    public long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
}