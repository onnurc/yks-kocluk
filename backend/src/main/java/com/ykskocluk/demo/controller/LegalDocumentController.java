package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.LegalDocumentResponse;
import com.ykskocluk.demo.enums.LegalDocumentType;
import com.ykskocluk.demo.service.LegalDocumentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/legal-documents")
public class LegalDocumentController {
    private final LegalDocumentService service;
    public LegalDocumentController(LegalDocumentService service) { this.service = service; }
    @GetMapping("/{type}/current")
    public LegalDocumentResponse current(@PathVariable LegalDocumentType type) { return service.current(type); }
    @GetMapping
    public List<LegalDocumentResponse> currentDocuments() { return service.currentDocuments(); }
}
