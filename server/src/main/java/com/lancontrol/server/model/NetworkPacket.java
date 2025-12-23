package com.lancontrol.server.model;

public class NetworkPacket {
    private String command;
    private String token;
    private String payloadJson;
    private long timestamp;
    private String hmac;
    private long sequenceNumber;

    public String getHmac() {
        return hmac;
    }
    public void setHmac(String hmac) {
        this.hmac = hmac;
    }
    public long getSequenceNumber() {
        return sequenceNumber;
    }
    public void setSequenceNumber(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}