package com.haraldsson.aidocbackend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haraldsson.aidocbackend.ai.dto.AiResponseDTO;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiService {

    private final Logger log = LoggerFactory.getLogger(AiService.class);

    private final String openaiToken;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentService documentService;

    public AiService(DocumentService documentService) {
        this.documentService = documentService;
        this.openaiToken = System.getenv("OPENAI_API_TOKEN");

        if (openaiToken == null || openaiToken.isEmpty()) {
            log.warn("OPENAI_API_TOKEN environment variable is not set!");
        } else {
            log.info("AI Service initialized with OpenAI token (length: {})",
                    openaiToken.length());
        }

        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + openaiToken)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(60))
                ))
                .build();

        log.debug("WebClient initialized for OpenAI API");
    }


    public Mono<AiResponseDTO> askQuestion(String question) {
        log.debug("Sending question to OpenAI: {}", truncateQuestion(question));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "gpt-3.5-turbo");
        requestBody.put("messages", new Object[]{
                Map.of("role", "user", "content", question)
        });
        requestBody.put("max_tokens", 3000);
        requestBody.put("temperature", 0.7);

        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.debug("OpenAI response received"))
                .doOnError(error -> log.error("OpenAI API error: {}", error.getMessage()))
                .flatMap(this::parseOpenAiResponse)
                .onErrorResume(e -> {
                    log.error("Failed to communicate with OpenAI: {}", e.getMessage());
                    return Mono.just(new AiResponseDTO(
                            "Could not communicate with AI service. Please try again.",
                            "error",
                            0
                    ));
                })
                .doOnSuccess(response -> {
                    if ("error".equals(response.model())) {
                        log.warn("AI response indicates error: {}", response.answer());
                    } else {
                        log.info("AI question processed successfully, tokens used: {}",
                                response.tokens());
                    }
                });
    }


    private Mono<AiResponseDTO> parseOpenAiResponse(String jsonresponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonresponse);
            JsonNode choices = root.get("choices");

            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                log.error("Invalid OpenAI response - no choices found");
                return Mono.just(new AiResponseDTO(
                        "Invalid response from AI service",
                        "unknown",
                        0
                ));
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");

            if (message != null && message.has("content")) {
                String content = message.get("content").asText().trim();
                String model = root.get("model").asText();
                JsonNode usage = root.get("usage");
                int tokens = usage != null ? usage.get("total_tokens").asInt() : 0;

                log.debug("OpenAI answer parsed successfully, model: {}, tokens: {}",
                        model, tokens);

                return Mono.just(new AiResponseDTO(content, model, tokens));
            }

            log.error("Could not extract content from OpenAI response");
            return Mono.just(new AiResponseDTO("could not extract OpenAI answer",
                    "unknown",
                    0
            ));

        } catch (Exception e) {
            log.error("Error parsing OpenAI response: {}", e.getMessage());
            return Mono.just(new AiResponseDTO("Error when extracting AI answer: ", "error", 0));
        }
    }

    public Mono<AiResponseDTO> askQuestionAboutDocument(String question, UUID userId) {
        log.info("AI question about documents from user {}: {}",
                maskUserId(userId), truncateQuestion(question));

        return documentService.findRelevantChunksWithEmbeddings(question, userId)
                .doOnSuccess(context ->
                        log.debug("Context found for question: {} chars",
                                context.length()))
                .flatMap(context -> {
                    log.info("No relevant documents found for question");

                    if (context == null ||
                            context.contains("No document") ||
                            context.contains("No chunks") ||
                            context.contains("Error")) {

                        log.info("No valid context found, using general AI");
                        return askQuestion(question);
                    }

                    String prompt = createDocumentPrompt(context, question);
                    log.debug("Created prompt with context length: {}", prompt.length());

                    return askQuestion(prompt);
                })
                .onErrorResume(e -> {
                    log.error("Error in AI service: {}", e.getMessage());
                    return Mono.just(new AiResponseDTO(
                            "Sorry, I couldn't process your question. Please try again.",
                            "error",
                            0
                    ));
                })
                .doOnSuccess(response -> {
                    if (!"error".equals(response.model())) {
                        log.info("Document question answered successfully for user {}, tokens: {}",
                                maskUserId(userId), response.tokens());
                    }
                });
    }

    private String createDocumentPrompt(String context, String question) {
        int maxContextLength = 13000;
        String truncatedContext;

        if (context.length() > maxContextLength) {
            log.debug("Truncating context from {} to {} characters",
                    context.length(), maxContextLength);
            truncatedContext = context.substring(0, maxContextLength) + "...";
        } else {
            truncatedContext = context;
        }

        String prompt = String.format(
                "Based on following document parts:\n\n%s\n\nAnswer this question in the same language as the question: %s\n\nGive a detailed answer based only on the provided text.",
                truncatedContext, question
        );

        log.debug("Created prompt with total length: {}", prompt.length());
        return prompt;
    }

    // hjälpmetod för dölja userid
    private String maskUserId(UUID userId) {
        if (userId == null) return "null";
        String idStr = userId.toString();
        return idStr.substring(0, Math.min(8, idStr.length())) + "***";
    }

    private String truncateQuestion(String question) {
        if (question == null) return "null";
        if (question.length() <= 100) return question;
        return question.substring(0, 100) + "...";
    }

}