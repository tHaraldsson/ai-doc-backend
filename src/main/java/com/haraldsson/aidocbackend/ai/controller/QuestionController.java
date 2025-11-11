package com.haraldsson.aidocbackend.ai.controller;

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
            @RequestBody AskQuestionRequestDTO request,
            @AuthenticationPrincipal CustomUser user
    ) {

        if (user == null) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        return documentService.getTextByUserId(user.getId())
                .flatMap(allText -> aiService.askQuestionAboutDocument(allText, request.getQuestion()))
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