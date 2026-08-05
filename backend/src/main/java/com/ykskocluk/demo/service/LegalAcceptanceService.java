package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.LegalOnboardingRequest;
import com.ykskocluk.demo.entity.LegalAcceptance;
import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.LegalAcceptanceType;
import com.ykskocluk.demo.enums.LegalDocumentType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.LegalAcceptanceRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class LegalAcceptanceService {
    private static final String REGISTRATION = "REGISTRATION";
    private final LegalDocumentService documentService;
    private final LegalAcceptanceRepository acceptanceRepository;
    private final UserRepository userRepository;

    public LegalAcceptanceService(LegalDocumentService documentService, LegalAcceptanceRepository acceptanceRepository,
                                  UserRepository userRepository) {
        this.documentService = documentService;
        this.acceptanceRepository = acceptanceRepository;
        this.userRepository = userRepository;
    }

    public RequiredDocuments validateRequired(Long termsDocumentId, Long explicitConsentDocumentId) {
        if (termsDocumentId == null || explicitConsentDocumentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_LEGAL_ACCEPTANCE_MISSING",
                    "Kullanım Koşulları ve Açık Rıza kabul edilmelidir");
        }
        LegalDocument terms = documentService.currentEntity(LegalDocumentType.TERMS_OF_USE);
        LegalDocument explicit = documentService.currentEntity(LegalDocumentType.EXPLICIT_CONSENT);
        LegalDocument submittedTerms = documentService.findById(termsDocumentId);
        LegalDocument submittedExplicit = documentService.findById(explicitConsentDocumentId);
        if (submittedTerms.getType() != LegalDocumentType.TERMS_OF_USE
                || submittedExplicit.getType() != LegalDocumentType.EXPLICIT_CONSENT
                || !terms.getId().equals(submittedTerms.getId())
                || !explicit.getId().equals(submittedExplicit.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "LEGAL_DOCUMENT_NOT_CURRENT",
                    "Gönderilen hukuki doküman güncel değil");
        }
        return new RequiredDocuments(terms, explicit);
    }

    public void recordRegistrationAcceptances(User user, RequiredDocuments documents,
                                               boolean emailOptIn, boolean smsOptIn) {
        record(user, documents.terms(), LegalAcceptanceType.ACCEPTED, REGISTRATION);
        record(user, documents.explicitConsent(), LegalAcceptanceType.ACCEPTED, REGISTRATION);
        if (emailOptIn) record(user, documents.explicitConsent(), LegalAcceptanceType.MARKETING_OPT_IN, "MARKETING_EMAIL");
        if (smsOptIn) record(user, documents.explicitConsent(), LegalAcceptanceType.MARKETING_OPT_IN, "MARKETING_SMS");
        user.setLegalOnboardingCompleted(true);
    }

    @Transactional
    public void completeOnboarding(Long userId, LegalOnboardingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        RequiredDocuments documents = validateRequired(request.termsDocumentId(), request.explicitConsentDocumentId());
        recordRegistrationAcceptances(user, documents, Boolean.TRUE.equals(request.marketingEmailOptIn()),
                Boolean.TRUE.equals(request.marketingSmsOptIn()));
    }

    public void requireCompleted(User user) {
        if (user != null && !user.isLegalOnboardingCompleted()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LEGAL_ONBOARDING_REQUIRED",
                    "Devam etmek için hukuki kayıt onaylarını tamamlayın");
        }
    }

    private void record(User user, LegalDocument document, LegalAcceptanceType type, String source) {
        if (acceptanceRepository.existsByUserIdAndLegalDocumentIdAndAcceptanceTypeAndSourceAndWithdrawnAtIsNull(
                user.getId(), document.getId(), type, source)) return;
        LegalAcceptance acceptance = new LegalAcceptance();
        acceptance.setUser(user);
        acceptance.setLegalDocument(document);
        acceptance.setDocumentType(document.getType());
        acceptance.setDocumentVersion(document.getDocumentVersion());
        acceptance.setDocumentContentHash(document.getContentHash());
        acceptance.setAcceptanceType(type);
        acceptance.setAcceptedAt(Instant.now());
        acceptance.setSource(source);
        acceptanceRepository.save(acceptance);
    }

    public record RequiredDocuments(LegalDocument terms, LegalDocument explicitConsent) { }
}
