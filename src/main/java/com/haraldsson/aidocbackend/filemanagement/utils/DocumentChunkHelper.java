package com.haraldsson.aidocbackend.filemanagement.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunkHelper {

    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkHelper.class);

    public float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0f;
        }

        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0f;
        }

        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    public String cleanTextForDatabase(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String cleaned = text.replace("\u0000", "")
                .replace("\u0001", "")
                .replace("\u0002", "")
                .replace("\u0003", "")
                .replace("\u0004", "")
                .replace("\u0005", "")
                .replace("\u0006", "")
                .replace("\u0007", "")
                .replace("\u0008", "")
                .replace("\u000B", "")
                .replace("\u000C", "")
                .replace("\u000E", "")
                .replace("\u000F", "");

        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");

        if (!text.equals(cleaned)) {
            logger.info("Text cleaned: removed {} problematic characters", (text.length() - cleaned.length()));
        }

        return cleaned;
    }
}