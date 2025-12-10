package com.haraldsson.aidocbackend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haraldsson.aidocbackend.ai.dto.AiResponseDTO;
import com.haraldsson.aidocbackend.filemanagement.service.DocumentService;
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

    private final String openaiToken;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentService documentService;

    public AiService(DocumentService documentService) {
        this.documentService = documentService;
        this.openaiToken = System.getenv("OPENAI_API_TOKEN");

        if (openaiToken == null || openaiToken.isEmpty()) {
            System.err.println("WARNING: OPENAI_API_TOKEN environment variable is not set!");
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
    }


    public Mono<AiResponseDTO> askQuestion(String question) {

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
                    .flatMap(this::parseOpenAiResponse)
                    .onErrorResume(e -> Mono.just(new AiResponseDTO(
                            "Could not communicate with OpenAI: " + e.getMessage(),
                            "error",
                            0
                    )));
    }



    private Mono<AiResponseDTO> parseOpenAiResponse(String jsonresponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonresponse);
            JsonNode choices = root.get("choices");
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");

            if (message != null && message.has("content")) {
                String content = message.get("content").asText().trim();
                String model = root.get("model").asText();
                JsonNode usage = root.get("usage");
                int tokens = usage != null ? usage.get("total_tokens").asInt() : 0;

                System.out.println("OpenAI answer: " + content);

                return Mono.just(new AiResponseDTO(content, model, tokens));
            }

            return Mono.just(new AiResponseDTO("could not extract OpenAI answer",
                    "unknown",
                    0));
        } catch (Exception e) {
            System.err.println("Error when parsing OpenAI answer: " + e.getMessage());
            return Mono.just(new AiResponseDTO("Error when extracting AI answer: ", "error", 0));
        }
    }

    public Mono<AiResponseDTO> askQuestionAboutDocument(String question, UUID userId) {
        System.out.println("AI question with embeddings: " + question);

        return documentService.findRelevantChunksWithEmbeddings(question, userId)
                .flatMap(context -> {
                    System.out.println("Context found: " + context.length() + " chars");

                    if (context.contains("Inga dokument") || context.contains("Inga chunks")) {
                        return askQuestion(question);
                    }

                    String prompt = createDocumentPrompt(context, question);
                    return askQuestion(prompt);
                })
                .onErrorResume(e -> {
                    System.err.println("Error in AI service: " + e.getMessage());
                    return Mono.just(new AiResponseDTO(
                            "Ett fel uppstod: " + e.getMessage(),
                            "error",
                            0
                    ));
                });
    }

    private String createDocumentPrompt(String context, String question) {
        String truncatedContext = context.length() > 13000
                ? context.substring(0, 13000) + "..."
                : context;

        return String.format(
                "Based on following document parts:\n\n%s\n\nAnswer this question in Swedish: %s\n\nGive a detailed answer based only on the provided text.",
                truncatedContext, question
        );
    }

    private int getDocumentCount(String context) {
        return context.split("=== DOCUMENT \\d+ ===").length - 1;
    }

    private String truncateContext(String context, int maxLength) {
        if (context.length() <= maxLength) {
            return context;
        }
        return context.substring(0, maxLength) + "...";
    }

}