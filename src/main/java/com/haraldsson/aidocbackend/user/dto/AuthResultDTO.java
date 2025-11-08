package com.haraldsson.aidocbackend.user.dto;

public class AuthResultDTO {
    private final String token;
    private final String username;

    public AuthResultDTO(String token, String username) {
        this.token = token;
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }
}
