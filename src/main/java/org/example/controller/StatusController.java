package org.example.controller;

import org.example.config.AiProperties;
import org.example.service.EmbeddingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/status")
public class StatusController {
    private final AiProperties aiProperties;
    private final EmbeddingService embeddingService;

    public StatusController(AiProperties aiProperties, EmbeddingService embeddingService) {
        this.aiProperties = aiProperties;
        this.embeddingService = embeddingService;
    }

    @GetMapping
    public Map<String, Object> status() {
        return Map.of(
                "ready", true,
                "aiConfigured", aiProperties.hasApiKey(),
                "chatModel", aiProperties.chatModel(),
                "visionModel", aiProperties.visionModel(),
                "embeddingModel", aiProperties.embeddingModel(),
                "mode", embeddingService.providerMode()
        );
    }
}
