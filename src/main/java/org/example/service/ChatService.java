package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.config.AiProperties;
import org.example.model.*;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    private static final String SYSTEM_PROMPT = """
            You are a practical RAG assistant for a knowledge-base chat application.
            Answer in the same language as the user whenever possible.
            Use the provided knowledge-base context when it is relevant.
            If the context is insufficient, say what is missing and avoid inventing facts.
            When images are provided, inspect them and combine visual evidence with retrieved text.
            """;

    private final AiProperties aiProperties;
    private final KnowledgeBaseService knowledgeBaseService;
    private final WebClient webClient;

    public ChatService(AiProperties aiProperties, KnowledgeBaseService knowledgeBaseService, WebClient.Builder builder) {
        this.aiProperties = aiProperties;
        this.knowledgeBaseService = knowledgeBaseService;
        this.webClient = builder
                .baseUrl(aiProperties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    public ChatResponse chat(ChatRequest request) {
        String userMessage = request.message() == null ? "" : request.message().trim();
        List<Citation> citations = request.useRag()
                ? knowledgeBaseService.search(userMessage, request.topK())
                : List.of();
        String context = knowledgeBaseService.buildContext(citations);

        if (aiProperties.hasApiKey()) {
            try {
                String answer = remoteChat(request, userMessage, context);
                return new ChatResponse(answer, citations, aiProperties.chatModel(), "remote-ai", Instant.now());
            } catch (RuntimeException exception) {
                String answer = localAnswer(userMessage, citations, request.images(), exception.getMessage());
                return new ChatResponse(answer, citations, "local-fallback", "local-demo", Instant.now());
            }
        }

        String answer = localAnswer(userMessage, citations, request.images(), null);
        return new ChatResponse(answer, citations, "local-demo", "local-demo", Instant.now());
    }

    private String remoteChat(ChatRequest request, String userMessage, String context) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (!context.isBlank()) {
            messages.add(Map.of(
                    "role", "system",
                    "content", "Knowledge-base context:\n" + context
            ));
        }
        safeHistory(request.history()).forEach(message ->
                messages.add(Map.of("role", normalizeRole(message.role()), "content", message.content()))
        );
        messages.add(Map.of("role", "user", "content", multimodalContent(userMessage, request.images())));

        JsonNode root = webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.apiKey())
                .bodyValue(Map.of(
                        "model", hasImages(request.images()) ? aiProperties.visionModel() : aiProperties.chatModel(),
                        "messages", messages,
                        "temperature", aiProperties.temperature()
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String answer = root == null ? null : root.at("/choices/0/message/content").asText(null);
        if (answer == null || answer.isBlank()) {
            throw new IllegalStateException("Chat response did not contain an answer.");
        }
        return answer;
    }

    private List<Object> multimodalContent(String userMessage, List<ImageAttachment> images) {
        List<Object> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", userMessage.isBlank() ? "Please analyze the uploaded image." : userMessage));
        if (images != null) {
            for (ImageAttachment image : images) {
                if (image.dataUrl() != null && !image.dataUrl().isBlank()) {
                    Map<String, Object> imageUrl = new LinkedHashMap<>();
                    imageUrl.put("url", image.dataUrl());
                    imageUrl.put("detail", "auto");
                    parts.add(Map.of("type", "image_url", "image_url", imageUrl));
                }
            }
        }
        return parts;
    }

    private List<ChatMessage> safeHistory(List<ChatMessage> history) {
        if (history == null) {
            return List.of();
        }
        return history.stream()
                .filter(message -> message.content() != null && !message.content().isBlank())
                .skip(Math.max(0, history.size() - 12))
                .toList();
    }

    private String normalizeRole(String role) {
        if ("assistant".equals(role) || "user".equals(role)) {
            return role;
        }
        return "user";
    }

    private boolean hasImages(List<ImageAttachment> images) {
        return images != null && images.stream().anyMatch(image -> image.dataUrl() != null && !image.dataUrl().isBlank());
    }

    private String localAnswer(String message, List<Citation> citations, List<ImageAttachment> images, String remoteError) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前运行在本地演示模式。");
        if (remoteError != null && !remoteError.isBlank()) {
            builder.append("远程模型调用失败，已自动回退。");
        }
        builder.append("\n\n");

        if (images != null && !images.isEmpty()) {
            builder.append("已收到 ").append(images.size()).append(" 张图片。未配置视觉模型 API Key 时，我可以保存并携带图片信息，但不能真正识别图片内容。\n\n");
        }

        if (citations.isEmpty()) {
            builder.append("知识库里暂时没有检索到可用片段。你可以先上传 txt、md、csv、json 或 pdf 文档，然后再提问。");
            if (message != null && !message.isBlank()) {
                builder.append("\n\n你的问题：").append(message);
            }
            return builder.toString();
        }

        builder.append("根据知识库检索，最相关的内容如下：\n");
        for (int i = 0; i < citations.size(); i++) {
            Citation citation = citations.get(i);
            builder.append("\n")
                    .append(i + 1)
                    .append(". ")
                    .append(citation.filename())
                    .append("，相关度 ")
                    .append(String.format("%.2f", citation.score()))
                    .append("\n")
                    .append(clip(citation.preview(), 420))
                    .append("\n");
        }
        builder.append("\n配置 `APP_AI_API_KEY` 后，这里会由真实大模型基于这些引用生成完整回答。");
        return builder.toString();
    }

    private String clip(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
