package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.LegalOnboardingRequest;
import com.ykskocluk.demo.dto.ExplicitConsentWithdrawalResponse;
import com.ykskocluk.demo.dto.SubscriptionCheckoutRequest;
import com.ykskocluk.demo.entity.LegalAcceptance;
import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
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
    private static final String SUBSCRIPTION_CHECKOUT = "SUBSCRIPTION_CHECKOUT";
    private final LegalDocumentService documentService;
    private final LegalAcceptanceRepository acceptanceRepository;
    private final UserRepository userRepository;
    private final MarketingPreferenceService marketingPreferenceService;

    public LegalAcceptanceService(LegalDocumentService documentService, LegalAcceptanceRepository acceptanceRepository,
                                  UserRepository userRepository, MarketingPreferenceService marketingPreferenceService) {
        this.documentService = documentService;
        this.acceptanceRepository = acceptanceRepository;
        this.userRepository = userRepository;
        this.marketingPreferenceService = marketingPreferenceService;
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

    public CheckoutDocuments validateCheckout(SubscriptionCheckoutRequest request) {
        if (request == null || !request.legalDocumentsAccepted()
                || request.preInformationDocumentId() == null
                || request.distanceSalesDocumentId() == null
                || request.refundCancellationPolicyDocumentId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_CHECKOUT_LEGAL_ACCEPTANCE_MISSING",
                    "Ödeme öncesi hukuki dokümanlar kabul edilmelidir");
        }

        LegalDocument submittedPreInformation = requireType(
                request.preInformationDocumentId(), LegalDocumentType.PRE_INFORMATION_FORM);
        LegalDocument submittedDistanceSales = requireType(
                request.distanceSalesDocumentId(), LegalDocumentType.DISTANCE_SALES_AGREEMENT);
        LegalDocument submittedRefundPolicy = requireType(
                request.refundCancellationPolicyDocumentId(), LegalDocumentType.REFUND_CANCELLATION_POLICY);

        LegalDocument currentPreInformation = documentService.currentEntity(LegalDocumentType.PRE_INFORMATION_FORM);
        LegalDocument currentDistanceSales = documentService.currentEntity(LegalDocumentType.DISTANCE_SALES_AGREEMENT);
        LegalDocument currentRefundPolicy = documentService.currentEntity(LegalDocumentType.REFUND_CANCELLATION_POLICY);

        if (!currentPreInformation.getId().equals(submittedPreInformation.getId())
                || !currentDistanceSales.getId().equals(submittedDistanceSales.getId())
                || !currentRefundPolicy.getId().equals(submittedRefundPolicy.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CHECKOUT_LEGAL_DOCUMENT_NOT_CURRENT",
                    "Gönderilen ödeme hukuki dokümanlarından biri güncel değil");
        }
        return new CheckoutDocuments(currentPreInformation, currentDistanceSales, currentRefundPolicy);
    }

    public void recordCheckoutAcceptances(User user, Subscription subscription, Payment payment,
                                          CheckoutDocuments documents) {
        recordCheckout(user, subscription, payment, documents.preInformation());
        recordCheckout(user, subscription, payment, documents.distanceSales());
        recordCheckout(user, subscription, payment, documents.refundCancellationPolicy());
    }

    public void recordRegistrationAcceptances(User user, RequiredDocuments documents,
                                               boolean emailOptIn, boolean smsOptIn) {
        record(user, documents.terms(), LegalAcceptanceType.ACCEPTED, REGISTRATION);
        record(user, documents.explicitConsent(), LegalAcceptanceType.ACCEPTED, REGISTRATION);
        marketingPreferenceService.recordRegistrationPreferences(user, emailOptIn, smsOptIn);
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

    @Transactional
    public ExplicitConsentWithdrawalResponse withdrawExplicitConsent(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        var active = acceptanceRepository
                .findByUserIdAndDocumentTypeAndAcceptanceTypeAndWithdrawnAtIsNull(
                        userId, LegalDocumentType.EXPLICIT_CONSENT, LegalAcceptanceType.ACCEPTED);
        Instant withdrawnAt = acceptanceRepository
                .findFirstByUserIdAndDocumentTypeAndAcceptanceTypeAndWithdrawnAtIsNotNullOrderByWithdrawnAtDesc(
                        userId, LegalDocumentType.EXPLICIT_CONSENT, LegalAcceptanceType.ACCEPTED)
                .map(LegalAcceptance::getWithdrawnAt).orElse(null);
        if (!active.isEmpty()) {
            withdrawnAt = Instant.now();
            Instant finalWithdrawnAt = withdrawnAt;
            active.forEach(acceptance -> acceptance.setWithdrawnAt(finalWithdrawnAt));
        }
        user.setLegalOnboardingCompleted(false);
        return new ExplicitConsentWithdrawalResponse(false, withdrawnAt);
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

    private LegalDocument requireType(Long documentId, LegalDocumentType expectedType) {
        LegalDocument document = documentService.findById(documentId);
        if (document.getType() != expectedType) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHECKOUT_LEGAL_DOCUMENT_TYPE_MISMATCH",
                    "Hukuki doküman beklenen türle eşleşmiyor");
        }
        return document;
    }

    private void recordCheckout(User user, Subscription subscription, Payment payment, LegalDocument document) {
        if (acceptanceRepository
                .existsBySubscriptionIdAndLegalDocumentIdAndAcceptanceTypeAndSourceAndWithdrawnAtIsNull(
                        subscription.getId(), document.getId(), LegalAcceptanceType.ACCEPTED,
                        SUBSCRIPTION_CHECKOUT)) {
            return;
        }
        LegalAcceptance acceptance = new LegalAcceptance();
        acceptance.setUser(user);
        acceptance.setLegalDocument(document);
        acceptance.setDocumentType(document.getType());
        acceptance.setDocumentVersion(document.getDocumentVersion());
        acceptance.setDocumentContentHash(document.getContentHash());
        acceptance.setAcceptanceType(LegalAcceptanceType.ACCEPTED);
        acceptance.setAcceptedAt(Instant.now());
        acceptance.setSource(SUBSCRIPTION_CHECKOUT);
        acceptance.setSubscription(subscription);
        acceptance.setPayment(payment);
        acceptanceRepository.save(acceptance);
    }

    public record RequiredDocuments(LegalDocument terms, LegalDocument explicitConsent) { }

    public record CheckoutDocuments(LegalDocument preInformation, LegalDocument distanceSales,
                                    LegalDocument refundCancellationPolicy) { }
}
