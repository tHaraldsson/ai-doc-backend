package com.haraldsson.aidocbackend.filemanagement.controller;

import com.haraldsson.aidocbackend.filemanagement.dto.UploadResponseDTO;
import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.model.DocumentChunk;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import org.springframework.http.HttpStatus;
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

    private final DocumentService documentService;

    public FileUploadController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Mono<ResponseEntity<UploadResponseDTO>> uploadPdf(
            @RequestPart("file") FilePart filePart,
            @AuthenticationPrincipal CustomUser user) {

        if (user == null) {
            System.out.println("User is NULL - returning 401");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new UploadResponseDTO("Not Authenticated", null, null)));
        }

        return documentService.processAndSavePdf(filePart, user)
                .map(savedDoc -> {
                    String preview = savedDoc.getContent().length() > 200
                            ? savedDoc.getContent().substring(0, 200) + "..."
                            : savedDoc.getContent();

                    return ResponseEntity.ok(new UploadResponseDTO(
                            "File uploaded successfully",
                            savedDoc.getFileName(),
                            preview
                    ));
                })
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new UploadResponseDTO("Upload failed: " + e.getMessage(), null, null)));
                });
    }


    @DeleteMapping("/deletedocument/{id}")
    public Mono<ResponseEntity<UploadResponseDTO>> deleteDocument(@PathVariable("id") UUID id) {

        return documentService.deleteDocument(id)
                .then(Mono.just(ResponseEntity.ok(new UploadResponseDTO(
                        "Document with id: " + id + " deleted successfully",
                        null,
                        null
                ))))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(new UploadResponseDTO("Error deleting document: " + e.getMessage(), null, null))));
    }

    @GetMapping("/documents")
    public Mono<ResponseEntity<Flux<Document>>> getUserDocuments(
            @AuthenticationPrincipal CustomUser user
    ) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        return Mono.just(ResponseEntity.ok(documentService.getAllDocuments(user.getId())));
    }

    @GetMapping("/textindb")
    public Mono<ResponseEntity<String>> getUserText(
            @AuthenticationPrincipal CustomUser user
    ) {
        if (user == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        return documentService.getTextByUserId(user.getId())
                .map(text -> ResponseEntity.ok(text))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body("Error: " + e.getMessage())));
    }

}
