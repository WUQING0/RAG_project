package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.config.AiProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {
    private static final int LOCAL_DIMENSIONS = 384;

    private final AiProperties aiProperties;
    private final WebClient webClient;

    public EmbeddingService(AiProperties aiProperties, WebClient.Builder webClientBuilder) {
        this.aiProperties = aiProperties;
        this.webClient = webClientBuilder
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    public double[] embed(String text) {
        if (aiProperties.hasApiKey()) {
            try {
                return remoteEmbedding(text);
            } catch (RuntimeException ignored) {
                return localEmbedding(text);
            }
        }
        return localEmbedding(text);
    }

    public String providerMode() {
        return aiProperties.hasApiKey() ? "remote-ai" : "local-demo";
    }

    private double[] remoteEmbedding(String text) {
        JsonNode root = webClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.apiKey())
                .bodyValue(Map.of(
                        "model", aiProperties.embeddingModel(),
                        "input", text
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        JsonNode embeddingNode = root == null ? null : root.at("/data/0/embedding");
        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new IllegalStateException("Embedding response did not contain a vector.");
        }
        double[] vector = new double[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            vector[i] = embeddingNode.get(i).asDouble();
        }
        return normalize(vector);
    }

    private double[] localEmbedding(String text) {
        double[] vector = new double[LOCAL_DIMENSIONS];
        for (String token : tokenize(text)) {
            String hash = sha256(token);
            int bucket = Math.floorMod(hash.hashCode(), LOCAL_DIMENSIONS);
            double sign = (hash.charAt(0) % 2 == 0) ? 1.0 : -1.0;
            vector[bucket] += sign * (1.0 + Math.min(token.length(), 16) / 16.0);
        }
        return normalize(vector);
    }

    private List<String> tokenize(String text) {
        String normalized = text == null ? "" : text.toLowerCase()
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+", " ")
                .trim();
        List<String> tokens = new ArrayList<>();
        for (String part : normalized.split("\\s+")) {
            if (!part.isBlank()) {
                tokens.add(part);
                if (part.length() > 3) {
                    for (int i = 0; i <= part.length() - 3; i++) {
                        tokens.add(part.substring(i, i + 3));
                    }
                }
            }
        }
        return tokens;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    public double cosine(double[] left, double[] right) {
        if (left.length != right.length) {
            return 0.0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double[] normalize(double[] vector) {
        double norm = 0;
        for (double value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            return vector;
        }
        double divisor = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / divisor;
        }
        return vector;
    }
}
