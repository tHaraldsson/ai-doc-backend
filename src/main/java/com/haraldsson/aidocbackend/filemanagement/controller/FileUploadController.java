package com.haraldsson.aidocbackend.filemanagement.controller;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    @PostMapping("/upload")
    public ResponseEntity<String> uploadPdf(@RequestParam("file") MultipartFile file) {
        return Optional.ofNullable(file)
                .filter(f -> !f.isEmpty())
                .map(f -> {
                    try (PDDocument document = PDDocument.load(f.getInputStream(), (String) null)) {
                        PDFTextStripper pdfStripper = new PDFTextStripper();
                        String text = pdfStripper.getText(document);


                        String preview = text.length() > 200 ? text.substring(0, 200) : text;
                        System.out.println("Preview: " + preview + "...");

                        // TODO: spara texten i databasen
                        return ResponseEntity.ok("File uploaded and text extracted successfully");
                    } catch (IOException e) {
                        e.printStackTrace();
                        return ResponseEntity.status(500).body("Error when handling file");
                    }
                })
                .orElseGet(() -> ResponseEntity.badRequest().body("No file selected"));
    }
}
