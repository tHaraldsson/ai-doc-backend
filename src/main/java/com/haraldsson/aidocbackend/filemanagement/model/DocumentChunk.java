package com.haraldsson.aidocbackend.filemanagement.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.util.UUID;

@Table("document_chunks")
public class DocumentChunk {

    @Id
    private UUID id;

    @Column("document_id")
    private UUID documentId;

    @Column("user_id")
    private UUID userId;

    @Column("filename")
    private String filename;

    @Column("content")
    private String content;

    @Column("chunk_number")
    private int chunkNumber;

    @Column("start_index")
    private int startIndex;

    @Column("end_index")
    private int endIndex;

    @Column("embedding_json")
    private String embeddingJson;

    public DocumentChunk() {}

    public DocumentChunk(UUID documentId, UUID userId, String filename,
                         String content, int chunkNumber, int startIndex, int endIndex) {
        this.documentId = documentId;
        this.userId = userId;
        this.filename = filename;
        this.content = content;
        this.chunkNumber = chunkNumber;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getChunkNumber() { return chunkNumber; }
    public void setChunkNumber(int chunkNumber) { this.chunkNumber = chunkNumber; }

    public int getStartIndex() { return startIndex; }
    public void setStartIndex(int startIndex) { this.startIndex = startIndex; }

    public int getEndIndex() { return endIndex; }
    public void setEndIndex(int endIndex) { this.endIndex = endIndex; }

    public String getEmbeddingJson() { return embeddingJson; }
    public void setEmbeddingJson(String embeddingJson) { this.embeddingJson = embeddingJson; }

    // Hjälpmetoder
    public float[] getEmbedding() {
        if (embeddingJson == null || embeddingJson.isEmpty()) {
            return null;
        }
        try {
            String clean = embeddingJson.replace("[", "").replace("]", "").trim();
            if (clean.isEmpty()) return null;

            String[] parts = clean.split(",");
            float[] embedding = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                embedding[i] = Float.parseFloat(parts[i].trim());
            }
            return embedding;
        } catch (Exception e) {
            System.err.println("Error parsing embedding: " + e.getMessage());
            return null;
        }
    }

    public void setEmbedding(float[] embedding) {
        if (embedding == null) {
            this.embeddingJson = null;
            return;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(", ");
        }
        sb.append("]");
        this.embeddingJson = sb.toString();
    }
}