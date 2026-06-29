package org.example.model;

import java.util.List;

public record ChatRequest(
        String message,
        List<ChatMessage> history,
        List<ImageAttachment> images,
        boolean useRag,
        int topK
) {
}
