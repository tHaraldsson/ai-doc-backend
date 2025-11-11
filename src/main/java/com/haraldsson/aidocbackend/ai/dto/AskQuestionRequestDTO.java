package com.haraldsson.aidocbackend.ai.dto;

public class AskQuestionRequestDTO {

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
