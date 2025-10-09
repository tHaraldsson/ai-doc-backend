package com.haraldsson.aidocbackend.ai.controller;

import com.haraldsson.aidocbackend.ai.service.AiService;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class QuestionController {

    private final DocumentService documentService;
    private final AiService aiService;

    public QuestionController(DocumentService documentService, AiService aiService) {
        this.documentService = documentService;
        this.aiService = aiService;
    }

    @GetMapping("/ask")
    public ResponseEntity<String> askQuestion(@RequestParam String question) {

            String allText = documentService.getAllText();

            String answer = aiService.askQuestionAboutDocument(allText, question);
            return ResponseEntity.ok(answer);
    }

    @GetMapping("/summarize")
    public ResponseEntity<String> summarizeDocument() {

            String allText = documentService.getAllText();

            String summary = aiService.summarizeDocument(allText);
            return ResponseEntity.ok("Summary: " + summary);
    }

    @GetMapping("/ask-direct")
    public ResponseEntity<String> askDirectQuestion(@RequestParam String question) {

            String answer = aiService.askQuestion(question);
            return ResponseEntity.ok("Answer: " + answer);
    }

    @GetMapping("/textindb")
    public ResponseEntity<String> getTextInDb() {

            String allText = documentService.getAllText();

            return ResponseEntity.ok(allText);
    }

    @GetMapping("/ai-test")
    public ResponseEntity<String> testAi() {

            String result = aiService.testConnection();
            return ResponseEntity.ok(result);
    }
}