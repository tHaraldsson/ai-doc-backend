package com.haraldsson.aidocbackend.filemanagement.controller;

import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QuestionController {

    private final DocumentService documentService;

    public QuestionController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/ask")
    public ResponseEntity<String> askQuestion(@RequestParam String question) {
        String allText = documentService.getAllText();

        // TODO: Ta emot svar från AI och skicka tillbaka + question
        String answer = "AIs answer " + question;

        return ResponseEntity.ok(answer);
    }
}
