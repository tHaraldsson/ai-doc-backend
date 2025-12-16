package com.haraldsson.aidocbackend.advice.exceptions;

public class FileProcessingException extends BusinessException {
    public FileProcessingException(String message) {
        super(message, "FILE_PROCESSING_ERROR");
    }

    public FileProcessingException(String message, Throwable cause) {
        super(message, cause, "FILE_PROCESSING_ERROR");
    }
}