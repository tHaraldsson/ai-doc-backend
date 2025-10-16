package com.haraldsson.aidocbackend.ai.dto;

public record AiResponseDTO(
        String answer,
        String model,
        int tokens
) {}
