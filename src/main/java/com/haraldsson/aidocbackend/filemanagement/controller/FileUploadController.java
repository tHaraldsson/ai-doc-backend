package com.haraldsson.aidocbackend.filemanagement.controller;

import com.haraldsson.aidocbackend.filemanagement.dto.UploadResponse;
import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    private final DocumentService documentService;

    public FileUploadController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Mono<ResponseEntity<UploadResponse>> uploadPdf(
            @RequestPart("file") FilePart filePart,
            @AuthenticationPrincipal CustomUser user) {

        if (user == null) {
            System.out.println("User is NULL - returning 401");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new UploadResponse("Not Authenticated", null, null)));
        }

        return documentService.processAndSavePdf(filePart, user)
                .map(savedDoc -> {
                    String preview = savedDoc.getContent().length() > 200
                            ? savedDoc.getContent().substring(0, 200) + "..."
                            : savedDoc.getContent();

                    return ResponseEntity.ok(new UploadResponse(
                            "File uploaded successfully",
                            savedDoc.getFileName(),
                            preview
                    ));
                })
                .onErrorResume(e -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new UploadResponse("Upload failed: " + e.getMessage(), null, null)));
                });
    }


    @DeleteMapping("/deletedocument/{id}")
    public Mono<ResponseEntity<UploadResponse>> deleteDocument(@PathVariable("id") UUID id) {

        return documentService.deleteDocument(id)
                .then(Mono.just(ResponseEntity.ok(new UploadResponse(
                        "Document with id: " + id + " deleted successfully",
                        null,
                        null
                ))))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(new UploadResponse("Error deleting document: " + e.getMessage(), null, null))));
    }

    @GetMapping("/documents")
    public Mono<ResponseEntity<Flux<Document>>> getAllDocuments() {
        return Mono.just(ResponseEntity.ok(documentService.getAllDocuments()));
    }

    @GetMapping("/textindb")
    public Mono<ResponseEntity<String>> getTextInDb() {
        return documentService.getAllText()
                .map(text -> ResponseEntity.ok(text))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body("Error: " + e.getMessage())));
    }

}
