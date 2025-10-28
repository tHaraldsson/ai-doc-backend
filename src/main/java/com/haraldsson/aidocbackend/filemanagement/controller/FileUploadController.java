package com.haraldsson.aidocbackend.filemanagement.controller;

import com.haraldsson.aidocbackend.filemanagement.dto.UploadResponse;
import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    private final DocumentService documentService;

    public FileUploadController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public Mono<ResponseEntity<UploadResponse>> uploadPdf(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(new UploadResponse("No file selected", null, null)));
        }

        return Mono.fromCallable(() -> {
                    try (PDDocument document = PDDocument.load(file.getInputStream(), (String) null)) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        return stripper.getText(document);
                    } catch (IOException e) {
                        throw new RuntimeException("Error handling file", e);
                    }
                })
                .flatMap(text -> {
                    Document document = new Document(file.getOriginalFilename(), text);
                    String preview = text.length() > 200 ? text.substring(0, 200) : text;

                    return documentService.save(document)
                            .map(savedDoc -> ResponseEntity.ok(new UploadResponse(
                                    "File uploaded and text extracted successfully",
                                    file.getOriginalFilename(),
                                    preview + "..."
                            )));
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500)
                        .body(new UploadResponse("Error when handling file", null, null))));
    }


    @DeleteMapping("/deletedocument/{id}")
    public Mono<ResponseEntity<UploadResponse>> deleteDocument(@PathVariable("id") Long id) {

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
