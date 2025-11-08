package com.haraldsson.aidocbackend.user.dto;

public class AuthResponse {

    private String username;
    private String message;
    private boolean success;

    public AuthResponse(String message, String username) {
        this.username = username;
        this.success = true;
        this.message = message;
    }

    public AuthResponse(String errorMessage) {
        this.username = null;
        this.success = false;
        this.message = errorMessage;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
