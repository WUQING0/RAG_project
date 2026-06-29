package org.example.model;

import java.time.Instant;
import java.util.List;

public record ChatResponse(
        String answer,
        List<Citation> citations,
        String model,
        String mode,
        Instant createdAt
) {
}
