package com.haraldsson.aidocbackend.filemanagement.model;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;


@Table("documents")
public class Document {

    @Id
    private UUID id;

    @NotBlank(message = "Filename cannot be empty")
    @Column("file_name")
    private String fileName;

    @Column("content")
    private String content;

    @Column("user_id")
    private UUID userId;

    @Column("created_at")
    private LocalDateTime createdAt;

    public Document() {
    }

    public Document(String fileName, String content, UUID userId) {
        this.fileName = fileName;
        this.content = content;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }



    public UUID getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
