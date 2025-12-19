package com.haraldsson.aidocbackend.filemanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmbeddingService() {
        String token = System.getenv("OPENAI_API_TOKEN");

        if (token == null || token.isEmpty()) {
            logger.warn("OPENAI_API_TOKEN environment variable is not set!");
        } else {
            logger.info("EmbeddingService initialized with token present (length: {})", token.length());
        }

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(60))
                                .addHandlerLast(new WriteTimeoutHandler(60)));

        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Mono<float[]> createEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("Cannot create embedding for empty or null text");
            return Mono.empty();
        }

        String truncatedText = text.length() > 8000
                ? text.substring(0, 8000)
                : text;

        logger.debug("Creating embedding for text ({} chars, truncated from {} chars)",
                truncatedText.length(), text.length());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "text-embedding-ada-002");
        requestBody.put("input", truncatedText);

        return webClient.post()
                .uri("/embeddings")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(45))  // Timeout per request
                .flatMap(this::parseEmbeddingResponse)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .filter(e -> e instanceof ReadTimeoutException))
                .doOnSuccess(embedding -> {
                    logger.info("Embedding created successfully: {} dimensions", embedding.length);
                })
                .doOnError(e -> {
                    logger.error("Error creating embedding after retries: {}", e.getMessage());
                })
                .onErrorResume(e -> {
                    logger.warn("Falling back to empty embedding due to error: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<float[]> parseEmbeddingResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.get("data");

            if (data == null || !data.isArray() || data.isEmpty()) {
                logger.error("No data in embedding response. JSON: {}", json);
                return Mono.empty();
            }

            JsonNode embeddingNode = data.get(0).get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                logger.error("No embedding in response. JSON: {}", json);
                return Mono.empty();
            }

            float[] embedding = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                embedding[i] = embeddingNode.get(i).floatValue();
            }

            logger.debug("Successfully parsed embedding with {} dimensions", embedding.length);
            return Mono.just(embedding);

        } catch (Exception e) {
            logger.error("Error parsing embedding JSON", e);
            return Mono.empty();
        }
    }
}