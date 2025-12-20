package com.haraldsson.aidocbackend.advice.dto;

import java.time.LocalDateTime;

public class SimpleErrorResponse {
    private final LocalDateTime timestamp;
    private final String error;
    private final String message;

    public SimpleErrorResponse(String error, String message) {
        this.timestamp = LocalDateTime.now();
        this.error = error;
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }
}