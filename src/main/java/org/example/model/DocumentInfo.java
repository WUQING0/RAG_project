package org.example.model;

import java.time.Instant;

public record DocumentInfo(
        String id,
        String filename,
        String contentType,
        long sizeBytes,
        int chunks,
        Instant uploadedAt
) {
}
