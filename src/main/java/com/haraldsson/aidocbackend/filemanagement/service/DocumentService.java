package com.haraldsson.aidocbackend.filemanagement.service;

import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Mono<String> getAllText() {

        return documentRepository.findAll()
                .map(Document::getContent)
                .reduce(new StringBuilder(), (sb, content) -> sb.append(content).append("\n"))
                .map(StringBuilder::toString);
    }

    public Flux<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public Mono<Document> save(Document document) {
       return documentRepository.save(document);
    }

    public Mono<Void> deleteDocument(Long id) {return documentRepository.deleteById(id); }
}
