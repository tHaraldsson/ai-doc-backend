package com.haraldsson.aidocbackend.ai.controller;

import com.haraldsson.aidocbackend.advice.exceptions.ValidationException;
import com.haraldsson.aidocbackend.ai.dto.AiResponseDTO;
import com.haraldsson.aidocbackend.ai.dto.AskQuestionRequestDTO;
import com.haraldsson.aidocbackend.ai.service.AiService;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping("/ask")
    public Mono<ResponseEntity<AiResponseDTO>> askQuestion(
            @RequestBody(required = false) AskQuestionRequestDTO request,
            @AuthenticationPrincipal CustomUser user
    ) {

        if (request == null) {
            return Mono.just(ResponseEntity.ok().build());
        }

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Mono.error(new ValidationException("Question is required"));
        }


        return aiService.askQuestionAboutDocument(request.getQuestion(), user.getId())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(new AiResponseDTO("Error: " + e.getMessage(), "error", 0))));
    }



    @GetMapping("/ask-direct")
    public Mono<ResponseEntity<AiResponseDTO>> askDirectQuestion(@RequestParam String question) {


            return aiService.askQuestion(question)
                    .map(ResponseEntity::ok)
                    .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                            .body(new AiResponseDTO("Error: " + e.getMessage(), "error", 0))));
    }
}