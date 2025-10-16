package com.haraldsson.aidocbackend.ai.controller;

import com.haraldsson.aidocbackend.ai.dto.AiResponseDTO;
import com.haraldsson.aidocbackend.ai.service.AiService;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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
    public Mono<ResponseEntity<AiResponseDTO>> askQuestion(@RequestParam String question) {

        try {
            String allText = documentService.getAllText();

            return aiService.askQuestionAboutDocument(allText, question)
                    .map(ResponseEntity::ok);
        } catch (Exception e) {
            return Mono.just(ResponseEntity.internalServerError()
                    .body(new AiResponseDTO("Error: " + e.getMessage(), "error", 0)));
        }
    }

    @GetMapping("/summarize")
    public ResponseEntity<String> summarizeDocument() {

            String allText = documentService.getAllText();

            String summary = aiService.summarizeDocument(allText);
            return ResponseEntity.ok("Summary: " + summary);
    }

    @GetMapping("/ask-direct")
    public Mono<ResponseEntity<AiResponseDTO>> askDirectQuestion(@RequestParam String question) {


            return aiService.askQuestion(question)
                    .map(ResponseEntity::ok)
                    .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                            .body(new AiResponseDTO("Error: " + e.getMessage(), "error", 0))));
    }

    @GetMapping("/textindb")
    public ResponseEntity<String> getTextInDb() {

            String allText = documentService.getAllText();

            return ResponseEntity.ok(allText);
    }

//    @GetMapping("/ai-test")
//    public ResponseEntity<String> testAi() {
//
//            String result = aiService.testConnection();
//            return ResponseEntity.ok(result);
//    }
}