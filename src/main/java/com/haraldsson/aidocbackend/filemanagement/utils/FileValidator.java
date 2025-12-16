package com.haraldsson.aidocbackend.filemanagement.utils;

import com.haraldsson.aidocbackend.advice.exceptions.ValidationException;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class FileValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>();

    static {
        ALLOWED_EXTENSIONS.add(".pdf");
        ALLOWED_EXTENSIONS.add(".xlsx");
        ALLOWED_EXTENSIONS.add(".xls");
        ALLOWED_EXTENSIONS.add(".pptx");
        ALLOWED_EXTENSIONS.add(".ppt");
    }

    public void validateFile(FilePart filePart) {
        if (filePart == null) {
            throw new ValidationException("No file provided");
        }

        String filename = filePart.filename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new ValidationException("Invalid filename");
        }

        validateFileExtension(filename);
    }

    private void validateFileExtension(String filename) {
        String lowerCaseFilename = filename.toLowerCase();
        boolean isValidExtension = false;

        for (String extension : ALLOWED_EXTENSIONS) {
            if (lowerCaseFilename.endsWith(extension)) {
                isValidExtension = true;
                break;
            }
        }

        if (!isValidExtension) {
            String allowedExtensionsString = String.join(", ", ALLOWED_EXTENSIONS);
            throw new ValidationException(
                    String.format("File type not supported. Allowed types: %s", allowedExtensionsString)
            );
        }
    }

    public String getFileExtension(String filename) {
        if (filename == null) {
            throw new ValidationException("Filename cannot be null");
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            throw new ValidationException("File has no extension");
        }
        return filename.substring(lastDotIndex).toLowerCase();
    }
}