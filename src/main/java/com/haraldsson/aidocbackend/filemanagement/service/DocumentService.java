package com.haraldsson.aidocbackend.filemanagement.service;

import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public String getAllText() {
        List<Document> documents = documentRepository.findAll();
        StringBuilder sb = new StringBuilder();
        for (Document document : documents) {
            sb.append(document.getContent()).append("\n");
        }
        return sb.toString();
    }
}
