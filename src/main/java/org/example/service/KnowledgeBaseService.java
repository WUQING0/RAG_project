package org.example.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.model.Citation;
import org.example.model.DocumentChunk;
import org.example.model.DocumentInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KnowledgeBaseService {
    private static final int CHUNK_SIZE = 900;
    private static final int CHUNK_OVERLAP = 160;

    private final EmbeddingService embeddingService;
    private final Path uploadDir;
    private final Map<String, DocumentInfo> documents = new ConcurrentHashMap<>();
    private final List<DocumentChunk> chunks = new ArrayList<>();

    public KnowledgeBaseService(
            EmbeddingService embeddingService,
            @Value("${app.knowledge.upload-dir:data/uploads}") String uploadDir
    ) throws IOException {
        this.embeddingService = embeddingService;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    public synchronized DocumentInfo ingest(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        String documentId = UUID.randomUUID().toString();
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        Path target = uploadDir.resolve(documentId + "-" + originalFilename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String text = extractText(target, contentType, originalFilename);
        List<String> textChunks = splitIntoChunks(text);
        for (int i = 0; i < textChunks.size(); i++) {
            String chunkText = textChunks.get(i);
            chunks.add(new DocumentChunk(documentId, originalFilename, i, chunkText, embeddingService.embed(chunkText)));
        }

        DocumentInfo info = new DocumentInfo(
                documentId,
                originalFilename,
                contentType,
                file.getSize(),
                textChunks.size(),
                Instant.now()
        );
        documents.put(documentId, info);
        return info;
    }

    public List<DocumentInfo> listDocuments() {
        return documents.values().stream()
                .sorted(Comparator.comparing(DocumentInfo::uploadedAt).reversed())
                .toList();
    }

    public synchronized void deleteDocument(String documentId) {
        documents.remove(documentId);
        chunks.removeIf(chunk -> chunk.documentId().equals(documentId));
    }

    public List<Citation> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(topK <= 0 ? 4 : topK, 10));
        double[] queryVector = embeddingService.embed(query);
        synchronized (this) {
            return chunks.stream()
                    .map(chunk -> toCitation(chunk, embeddingService.cosine(queryVector, chunk.embedding())))
                    .filter(citation -> citation.score() > 0)
                    .sorted(Comparator.comparingDouble(Citation::score).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    public String buildContext(List<Citation> citations) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < citations.size(); i++) {
            Citation citation = citations.get(i);
            builder.append("[")
                    .append(i + 1)
                    .append("] ")
                    .append(citation.filename())
                    .append(" #")
                    .append(citation.chunkIndex())
                    .append(":\n")
                    .append(citation.preview())
                    .append("\n\n");
        }
        return builder.toString().trim();
    }

    private Citation toCitation(DocumentChunk chunk, double score) {
        return new Citation(
                chunk.documentId(),
                chunk.filename(),
                chunk.chunkIndex(),
                score,
                chunk.text()
        );
    }

    private String extractText(Path file, String contentType, String filename) throws IOException {
        String lower = filename.toLowerCase();
        if (contentType.equals("application/pdf") || lower.endsWith(".pdf")) {
            try (PDDocument document = Loader.loadPDF(file.toFile())) {
                return new PDFTextStripper().getText(document);
            }
        }
        if (isPlainText(contentType, lower)) {
            return Files.readString(file, StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Unsupported file type. Please upload txt, md, csv, json, or pdf files.");
    }

    private boolean isPlainText(String contentType, String lowerFilename) {
        return contentType.startsWith("text/")
                || contentType.equals("application/json")
                || lowerFilename.endsWith(".md")
                || lowerFilename.endsWith(".txt")
                || lowerFilename.endsWith(".csv")
                || lowerFilename.endsWith(".json");
    }

    private List<String> splitIntoChunks(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return List.of("No extractable text was found in this document.");
        }

        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            if (end < normalized.length()) {
                int sentenceBoundary = Math.max(
                        normalized.lastIndexOf('。', end),
                        Math.max(normalized.lastIndexOf('.', end), normalized.lastIndexOf('\n', end))
                );
                if (sentenceBoundary > start + CHUNK_SIZE / 2) {
                    end = sentenceBoundary + 1;
                }
            }
            result.add(normalized.substring(start, end).trim());
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(0, end - CHUNK_OVERLAP);
        }
        return result;
    }

    private String sanitizeFilename(String originalFilename) {
        String fallback = "document.txt";
        String filename = originalFilename == null || originalFilename.isBlank() ? fallback : originalFilename;
        return filename.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }
}
