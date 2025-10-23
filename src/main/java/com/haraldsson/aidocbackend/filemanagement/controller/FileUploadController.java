package com.haraldsson.aidocbackend.filemanagement.controller;

import com.haraldsson.aidocbackend.filemanagement.dto.UploadResponse;
import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    public ResponseEntity<UploadResponse> uploadPdf(@RequestParam("file") MultipartFile file) {
        return Optional.ofNullable(file)
                .filter(f -> !f.isEmpty())
                .map(f -> {
                    try (PDDocument document = PDDocument.load(f.getInputStream(), (String) null)) {
                        PDFTextStripper pdfStripper = new PDFTextStripper();
                        String text = pdfStripper.getText(document);

                        Document doc = new Document();
                        doc.setFileName(f.getOriginalFilename());
                        doc.setContent(text);

                        documentService.save(doc);

                        String preview = text.length() > 200 ? text.substring(0, 200) : text;


                        return ResponseEntity.ok(new UploadResponse(
                                "File uploaded and text extracted successfully",
                                f.getOriginalFilename(),
                                preview + "..."
                        ));

                    } catch (IOException e) {
                        e.printStackTrace();

                        return ResponseEntity.status(500)
                                .body(new UploadResponse("Error when handling file", null, null));
                    }
                })
                .orElseGet(() -> ResponseEntity.badRequest()
                        .body(new UploadResponse("No file selected", null, null)));
                }


    @DeleteMapping("/deletedocument/{id}")
    public ResponseEntity<UploadResponse> deleteDocument(@PathVariable("id") Long id) {
        try {
            documentService.deleteDocument(id);

            return ResponseEntity.ok(new UploadResponse(
                    "Document deleted successfully",
                    null,
                    null
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new UploadResponse("Error when deleting document: " + e.getMessage(), null, null));
        }
    }

        @GetMapping("/documents")
        public ResponseEntity<List<Document>> getAllDocuments() {
            List<Document> documents = documentService.getAllDocuments();
            return ResponseEntity.ok(documents);
        }

    @GetMapping("/textindb")
    public ResponseEntity<String> getTextInDb() {

        String allText = documentService.getAllText();

        return ResponseEntity.ok(allText);
    }

}
