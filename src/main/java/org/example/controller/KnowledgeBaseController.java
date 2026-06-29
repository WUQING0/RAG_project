package org.example.controller;

import org.example.model.Citation;
import org.example.model.DocumentInfo;
import org.example.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/documents")
    public DocumentInfo upload(@RequestParam("file") MultipartFile file) throws IOException {
        return knowledgeBaseService.ingest(file);
    }

    @GetMapping("/documents")
    public List<DocumentInfo> documents() {
        return knowledgeBaseService.listDocuments();
    }

    @DeleteMapping("/documents/{documentId}")
    public void delete(@PathVariable String documentId) {
        knowledgeBaseService.deleteDocument(documentId);
    }

    @GetMapping("/search")
    public List<Citation> search(@RequestParam String q, @RequestParam(defaultValue = "4") int topK) {
        return knowledgeBaseService.search(q, topK);
    }
}
