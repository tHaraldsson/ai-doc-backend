package com.haraldsson.aidocbackend.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class AiService {

    private final String openaiToken;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiService() {
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


    public String askQuestion(String question) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("messages", new Object[]{
                    Map.of("role", "user", "content", question)
            });
            requestBody.put("max_tokens", 1000);
            requestBody.put("temperature", 0.7);

            Mono<String> response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class);

            String jsonResponse = response.block();
            return extractOpenAIAnswer(jsonResponse);

        } catch (Exception e) {
            System.err.println("OpenAI API error: " + e.getMessage());
            return "Could not communicate with OpenAI: " + e.getMessage();
        }
    }

    public String summarizeDocument(String text) {
        try {
            String prompt = "Summarize following text in 2-3 sentences and in swedish:\n\n" +
                    truncateContext(text, 4000);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("messages", new Object[]{
                    Map.of("role", "user", "content", prompt)
            });
            requestBody.put("max_tokens", 200);
            requestBody.put("temperature", 0.5);

            Mono<String> response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class);

            String jsonResponse = response.block();
            return extractOpenAIAnswer(jsonResponse);

        } catch (Exception e) {
            return "Could not summarize document: " + e.getMessage();
        }
    }

    public String testConnection() {
        try {

            String result = askQuestion("Hello! Is the connection working? Answer in swedish.");

            if (result != null && !result.contains("saknas") && !result.contains("fel")) {
                return "Connection to Open-AI succeeded! Test answer: " + result;
            } else {
                return "Connection failure. Answer: " + result;
            }

        } catch (Exception e) {
            return "Connection error: " + e.getMessage();
        }
    }

    private String extractOpenAIAnswer(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode choices = root.get("choices");

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");

            if (message != null && message.has("content")) {
                String content = message.get("content").asText();
                System.out.println("OpenAI answer: " + content);
                return content.trim();
            }

            return "Could not extract OpenAI answer";

        } catch (Exception e) {
            System.err.println("Error when parsing OpenAI answer: " + e.getMessage());
            return "Error when extracting AI answer";
        }
    }

    public String askQuestionAboutDocument(String context, String question) {

        String prompt = createDocumentPrompt(context, question);
        return askQuestion(prompt);
    }

    private String createDocumentPrompt(String context, String question) {
        String truncatedContext = truncateContext(context, 13000);
        return String.format(
                "Based on following document:\n\n\"%s\"\n\nAnswer this question in swedish: %s\n\nGive a short answer.",
                truncatedContext, question
        );
    }

    private String truncateContext(String context, int maxLength) {
        if (context.length() <= maxLength) {
            return context;
        }
        return context.substring(0, maxLength) + "...";
    }

}