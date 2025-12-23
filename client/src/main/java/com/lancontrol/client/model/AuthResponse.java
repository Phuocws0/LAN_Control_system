package com.lancontrol.client.model;

public class AuthResponse {
    private boolean success;
    private String message;
    private String newToken;

    public AuthResponse(boolean success, String message, String newToken) {
        this.success = success;
        this.message = message;
        this.newToken = newToken;
    }
    public AuthResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNewToken() {
        return newToken;
    }

    public void setNewToken(String newToken) {
        this.newToken = newToken;
    }
}