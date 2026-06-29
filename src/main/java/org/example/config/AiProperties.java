package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String baseUrl,
        String apiKey,
        String chatModel,
        String visionModel,
        String embeddingModel,
        int embeddingDimensions,
        double temperature
) {
    public AiProperties {
        baseUrl = valueOrDefault(baseUrl, "https://api.openai.com/v1");
        chatModel = valueOrDefault(chatModel, "gpt-4.1-mini");
        visionModel = valueOrDefault(visionModel, "gpt-4.1-mini");
        embeddingModel = valueOrDefault(embeddingModel, "text-embedding-3-small");
        embeddingDimensions = embeddingDimensions <= 0 ? 1536 : embeddingDimensions;
        temperature = temperature < 0 ? 0.2 : temperature;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
