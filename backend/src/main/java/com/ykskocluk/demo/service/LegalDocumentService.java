package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.LegalDocumentResponse;
import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.enums.LegalDocumentStatus;
import com.ykskocluk.demo.enums.LegalDocumentType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.LegalDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class LegalDocumentService {
    private final LegalDocumentRepository repository;
    public LegalDocumentService(LegalDocumentRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public LegalDocument currentEntity(LegalDocumentType type) {
        return repository.findFirstByTypeAndStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(
                        type, LegalDocumentStatus.PUBLISHED, Instant.now())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEGAL_DOCUMENT_NOT_FOUND",
                        "Güncel hukuki doküman bulunamadı"));
    }

    @Transactional(readOnly = true)
    public LegalDocumentResponse current(LegalDocumentType type) { return toResponse(currentEntity(type)); }

    @Transactional(readOnly = true)
    public LegalDocument findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "LEGAL_DOCUMENT_NOT_FOUND", "Hukuki doküman bulunamadı"));
    }

    @Transactional(readOnly = true)
    public List<LegalDocumentResponse> currentDocuments() {
        return repository.findByStatusAndEffectiveAtLessThanEqualOrderByTypeAsc(
                LegalDocumentStatus.PUBLISHED, Instant.now()).stream().map(this::toResponse).toList();
    }

    private LegalDocumentResponse toResponse(LegalDocument d) {
        return new LegalDocumentResponse(d.getId(), d.getType(), d.getDocumentVersion(), d.getTitle(),
                d.getContent(), d.getContentHash(), d.getEffectiveAt());
    }
}
