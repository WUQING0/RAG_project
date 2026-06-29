package org.example.model;

public record DocumentChunk(
        String documentId,
        String filename,
        int chunkIndex,
        String text,
        double[] embedding
) {
}
