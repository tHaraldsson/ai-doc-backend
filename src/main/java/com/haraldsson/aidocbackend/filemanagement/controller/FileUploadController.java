package com.haraldsson.aidocbackend.filemanagement.controller;

import com.haraldsson.aidocbackend.filemanagement.dto.UploadResponseDTO;
import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import com.haraldsson.aidocbackend.filemanagement.utils.FileValidator;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    private final DocumentService documentService;
    private final FileValidator fileValidator;

    public FileUploadController(DocumentService documentService, FileValidator fileValidator) {
        this.documentService = documentService;
        this.fileValidator = fileValidator;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Mono<ResponseEntity<UploadResponseDTO>> uploadFile(
            @RequestPart("file") FilePart filePart,
            @AuthenticationPrincipal CustomUser user) {

        log.info("File upload requested by user {}: {}", user.getId(), filePart.filename());

        fileValidator.validateFile(filePart);

        return documentService.processAndSaveFile(filePart, user)
                .map(savedDoc -> {
                    String preview = savedDoc.getContent() != null && savedDoc.getContent().length() > 200
                            ? savedDoc.getContent().substring(0, 200) + "..."
                            : savedDoc.getContent();

                    return ResponseEntity.ok(new UploadResponseDTO(
                            "File uploaded successfully",
                            savedDoc.getFileName(),
                            preview
                    ));
                });
    }

    @DeleteMapping("/deletedocument/{id}")
    public Mono<ResponseEntity<UploadResponseDTO>> deleteDocument(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUser user) {

        log.info("Delete document requested: documentId={}, userId={}", id, user.getId());

        return documentService.deleteDocument(id)
                .then(Mono.just(ResponseEntity.ok(
                        new UploadResponseDTO("Document deleted successfully", null, null)
                )));
    }

    @GetMapping("/documents")
    public Flux<Document> getUserDocuments(
            @AuthenticationPrincipal CustomUser user) {

        log.info("Get documents requested for user: {}", user.getId());
        return documentService.getAllDocuments(user.getId());
    }

    @GetMapping("/textindb")
    public Mono<ResponseEntity<String>> getUserText(
            @AuthenticationPrincipal CustomUser user) {

        log.info("Get user text requested for user: {}", user.getId());
        return documentService.getTextByUserId(user.getId())
                .map(text -> ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(text))
                .defaultIfEmpty(ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("No documents found for this user"));
    }
}