package com.haraldsson.aidocbackend.filemanagement.service;

import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.repository.DocumentRepository;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import com.haraldsson.aidocbackend.user.service.CustomUserService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.UUID;


@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CustomUserService customUserService;

    @Autowired
    public DocumentService(DocumentRepository documentRepository, CustomUserService customUserService) {
        this.documentRepository = documentRepository;
        this.customUserService = customUserService;
    }

    public Mono<Document> processAndSavePdf(FilePart filePart, CustomUser user) {

        return convertFilePartToBytes(filePart)
                .flatMap(pdfBytes -> extractTextFromPdf(pdfBytes))
                .flatMap(text -> createAndSaveDocument(filePart.filename(), text, user.getId()));
    }

    private Mono<byte[]> convertFilePartToBytes(FilePart filePart) {

        return filePart.content()
                .collectList()
                .map(dataBuffers -> {
                    int totalSize = dataBuffers.stream().mapToInt(db -> db.readableByteCount()).sum();

                    byte[] bytes = new byte[totalSize];
                    int offset = 0;

                    for (var buffer : dataBuffers) {
                        int count = buffer.readableByteCount();
                        buffer.read(bytes, offset, count);
                        offset += count;
                    }
                    return bytes;
                });
    }

    private Mono<String> extractTextFromPdf(byte[] pdfBytes) {
        return Mono.fromCallable(() -> {
            try (PDDocument document = PDDocument.load(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            } catch (IOException e) {
                throw new RuntimeException("PDF processing error: " + e.getMessage(), e);
            }
        });
    }

    private Mono<Document> createAndSaveDocument(String filename, String content, UUID id) {
        Document document = new Document(filename, content, id);
        return documentRepository.save(document);
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

    public Mono<Void> deleteDocument(UUID id) {return documentRepository.deleteById(id); }
}
