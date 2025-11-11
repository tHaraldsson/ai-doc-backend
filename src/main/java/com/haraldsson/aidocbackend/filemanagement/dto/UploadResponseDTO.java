package com.haraldsson.aidocbackend.filemanagement.dto;

public record UploadResponseDTO(
        String message,
        String filename,
        String preview
) {}
