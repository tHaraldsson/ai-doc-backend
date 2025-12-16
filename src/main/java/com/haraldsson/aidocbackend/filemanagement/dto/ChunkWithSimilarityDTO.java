package com.haraldsson.aidocbackend.filemanagement.dto;

import com.haraldsson.aidocbackend.filemanagement.model.DocumentChunk;

public class ChunkWithSimilarityDTO {
    private DocumentChunk chunk;
    private float similarity;

    public ChunkWithSimilarityDTO() {}

    public ChunkWithSimilarityDTO(DocumentChunk chunk, float similarity) {
        this.chunk = chunk;
        this.similarity = similarity;
    }

    public DocumentChunk getChunk() { return chunk; }
    public void setChunk(DocumentChunk chunk) { this.chunk = chunk; }

    public float getSimilarity() { return similarity; }
    public void setSimilarity(float similarity) { this.similarity = similarity; }
}