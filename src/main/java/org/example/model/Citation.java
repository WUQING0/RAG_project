package org.example.model;

public record Citation(String documentId, String filename, int chunkIndex, double score, String preview) {
}
