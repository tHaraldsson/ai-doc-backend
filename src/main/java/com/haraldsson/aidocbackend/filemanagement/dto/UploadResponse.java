package com.haraldsson.aidocbackend.filemanagement.dto;

public record UploadResponse(
        String message,
        String filename,
        String preview
) {}
