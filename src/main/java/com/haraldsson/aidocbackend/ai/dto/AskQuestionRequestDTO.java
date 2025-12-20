package com.haraldsson.aidocbackend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AskQuestionRequestDTO {

    @NotBlank(message = "Question cannot be empty")
    @Size(min = 1, max = 5000, message = "Question must be between 1 and 5000 characters")
    private String question;
    private String documentId;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }
}
