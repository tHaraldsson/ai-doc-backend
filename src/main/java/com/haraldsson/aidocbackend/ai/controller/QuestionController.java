package com.haraldsson.aidocbackend.ai.controller;

import com.haraldsson.aidocbackend.advice.exceptions.ValidationException;
import com.haraldsson.aidocbackend.ai.dto.AiResponseDTO;
import com.haraldsson.aidocbackend.ai.dto.AskQuestionRequestDTO;
import com.haraldsson.aidocbackend.ai.service.AiService;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class QuestionController {

    private final Logger log = LoggerFactory.getLogger(QuestionController.class);

    private final DocumentService documentService;
    private final AiService aiService;

    public QuestionController(DocumentService documentService, AiService aiService) {
        this.documentService = documentService;
        this.aiService = aiService;
        log.debug("QuestionController initialized");
    }

    @PostMapping("/ask")
    public Mono<ResponseEntity<AiResponseDTO>> askQuestion(
            @Valid @RequestBody(required = false) AskQuestionRequestDTO request,
            @AuthenticationPrincipal CustomUser user
    ) {

        if (request == null) {
            log.warn("Received null request in /api/ask");
            return Mono.just(ResponseEntity.badRequest()
                    .body(new AiResponseDTO("Request body is required", "error", 0)));
        }

        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            log.warn("Empty question from user: {}", maskUsername(user.getUsername()));
            return Mono.error(new ValidationException("Question is required"));
        }

        String maskedUsername = maskUsername(user.getUsername());
        String truncatedQuestion = truncateQuestion(request.getQuestion());

        log.info("Question request from user {}: {}",
                maskedUsername, truncatedQuestion);

        return aiService.askQuestionAboutDocument(request.getQuestion(), user.getId())
                .doOnSubscribe(s -> log.debug("Starting AI processing for user: {}",
                        maskedUsername))
                .map(response -> {
                    log.debug("AI response ready for user: {}", maskedUsername);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Error processing question for user {}: {}",
                            maskedUsername, e.getMessage(), e);
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(new AiResponseDTO(
                                    "An internal error occurred. Please try again later.",
                                    "error",
                                    0
                            )));
                })
                .doOnTerminate(() -> log.debug("Question processing completed for user: {}",
                        maskedUsername));
    }

    @GetMapping("/ask-direct")
    public Mono<ResponseEntity<AiResponseDTO>> askDirectQuestion(@RequestParam String question) {


            return aiService.askQuestion(question)
                    .map(ResponseEntity::ok)
                    .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                            .body(new AiResponseDTO("Error: " + e.getMessage(), "error", 0))));
    }

    // hjälpmetod för dölja username
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.substring(0, 2) + "***";
    }

    private String truncateQuestion(String question) {
        if (question == null) return "null";
        if (question.length() <= 50) return question;
        return question.substring(0, 50) + "...";
    }
}