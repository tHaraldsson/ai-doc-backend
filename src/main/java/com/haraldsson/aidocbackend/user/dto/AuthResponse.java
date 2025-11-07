package com.haraldsson.aidocbackend.user.dto;

public class AuthResponse {

    private String token;
    private String username;
    private String message;
    private boolean success;

    public AuthResponse(String token, String username) {
        this.token = token;
        this.username = username;
        this.success = true;
        this.message = "Login successful";
    }

    public AuthResponse(String errorMessage) {
        this.token = null;
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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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
