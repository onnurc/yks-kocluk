package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.LegalOnboardingRequest;
import com.ykskocluk.demo.dto.SubscriptionCheckoutRequest;
import com.ykskocluk.demo.entity.LegalAcceptance;
import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.entity.Payment;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.LegalDocumentType;
import com.ykskocluk.demo.enums.LegalDocumentStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.LegalAcceptanceRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalAcceptanceServiceTest {
    @Mock LegalDocumentService documentService;
    @Mock LegalAcceptanceRepository acceptanceRepository;
    @Mock UserRepository userRepository;
    @Mock MarketingPreferenceService marketingPreferenceService;
    LegalAcceptanceService service;
    LegalDocument terms;
    LegalDocument explicit;
    LegalDocument preInformation;
    LegalDocument distanceSales;
    LegalDocument refundPolicy;

    @BeforeEach
    void setUp() {
        service = new LegalAcceptanceService(documentService, acceptanceRepository, userRepository,
                marketingPreferenceService);
        terms = document(3L, LegalDocumentType.TERMS_OF_USE);
        explicit = document(2L, LegalDocumentType.EXPLICIT_CONSENT);
        preInformation = document(6L, LegalDocumentType.PRE_INFORMATION_FORM);
        distanceSales = document(7L, LegalDocumentType.DISTANCE_SALES_AGREEMENT);
        refundPolicy = document(8L, LegalDocumentType.REFUND_CANCELLATION_POLICY);
    }

    @Test
    void validateRequired_missingAcceptance_rejected() {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.validateRequired(null, 2L));
        assertThat(ex.getErrorCode()).isEqualTo("REQUIRED_LEGAL_ACCEPTANCE_MISSING");
        verifyNoInteractions(documentService);
    }

    @Test
    void validateRequired_missingExplicitConsent_rejected() {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.validateRequired(3L, null));
        assertThat(ex.getErrorCode()).isEqualTo("REQUIRED_LEGAL_ACCEPTANCE_MISSING");
        verifyNoInteractions(documentService);
    }

    @Test
    void validateRequired_currentDocuments_succeeds() {
        stubCurrent();
        var result = service.validateRequired(3L, 2L);
        assertThat(result.terms()).isSameAs(terms);
        assertThat(result.explicitConsent()).isSameAs(explicit);
    }

    @Test
    void validateRequired_staleOrRetiredDocument_rejected() {
        LegalDocument oldTerms = document(8L, LegalDocumentType.TERMS_OF_USE);
        when(documentService.currentEntity(LegalDocumentType.TERMS_OF_USE)).thenReturn(terms);
        when(documentService.currentEntity(LegalDocumentType.EXPLICIT_CONSENT)).thenReturn(explicit);
        when(documentService.findById(8L)).thenReturn(oldTerms);
        when(documentService.findById(2L)).thenReturn(explicit);
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.validateRequired(8L, 2L));
        assertThat(ex.getErrorCode()).isEqualTo("LEGAL_DOCUMENT_NOT_CURRENT");
    }

    @Test
    void recordRegistration_optionalMarketingDoesNotBlockAndIsSeparate() {
        User user = user(10L, false);
        service.recordRegistrationAcceptances(user,
                new LegalAcceptanceService.RequiredDocuments(terms, explicit), false, false);
        ArgumentCaptor<LegalAcceptance> captor = ArgumentCaptor.forClass(LegalAcceptance.class);
        verify(acceptanceRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(a -> a.getAcceptanceType().name().equals("ACCEPTED"));
        assertThat(user.isLegalOnboardingCompleted()).isTrue();
    }

    @Test
    void recordRegistration_selectedMarketingChannelsPersistSeparately() {
        User user = user(10L, false);
        service.recordRegistrationAcceptances(user,
                new LegalAcceptanceService.RequiredDocuments(terms, explicit), true, true);
        ArgumentCaptor<LegalAcceptance> captor = ArgumentCaptor.forClass(LegalAcceptance.class);
        verify(acceptanceRepository, times(2)).save(captor.capture());
        List<String> sources = captor.getAllValues().stream().map(LegalAcceptance::getSource).toList();
        assertThat(sources).containsOnly("REGISTRATION");
        verify(marketingPreferenceService).recordRegistrationPreferences(user, true, true);
    }

    @Test
    void withdrawExplicitConsent_preservesEvidenceAndReopensOnboarding() {
        User user = user(10L, true);
        LegalAcceptance explicitAcceptance = new LegalAcceptance();
        explicitAcceptance.setDocumentType(LegalDocumentType.EXPLICIT_CONSENT);
        LegalAcceptance termsAcceptance = new LegalAcceptance();
        termsAcceptance.setDocumentType(LegalDocumentType.TERMS_OF_USE);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(acceptanceRepository.findByUserIdAndDocumentTypeAndAcceptanceTypeAndWithdrawnAtIsNull(
                eq(10L), eq(LegalDocumentType.EXPLICIT_CONSENT), any()))
                .thenReturn(List.of(explicitAcceptance));

        var response = service.withdrawExplicitConsent(10L);

        assertThat(response.legalOnboardingCompleted()).isFalse();
        assertThat(response.withdrawnAt()).isNotNull();
        assertThat(explicitAcceptance.getWithdrawnAt()).isNotNull();
        assertThat(termsAcceptance.getWithdrawnAt()).isNull();
        assertThat(user.isLegalOnboardingCompleted()).isFalse();
        verify(acceptanceRepository, never()).delete(any());
    }

    @Test
    void withdrawExplicitConsent_repeatedCallIsIdempotent() {
        User user = user(10L, false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(acceptanceRepository.findByUserIdAndDocumentTypeAndAcceptanceTypeAndWithdrawnAtIsNull(
                eq(10L), eq(LegalDocumentType.EXPLICIT_CONSENT), any())).thenReturn(List.of());

        var response = service.withdrawExplicitConsent(10L);

        assertThat(response.legalOnboardingCompleted()).isFalse();
        verify(acceptanceRepository, never()).save(any());
        verify(acceptanceRepository, never()).delete(any());
    }

    @Test
    void oauthUser_cannotUseProtectedActionsBeforeOnboarding() {
        User oauthUser = user(10L, false);
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.requireCompleted(oauthUser));
        assertThat(ex.getErrorCode()).isEqualTo("LEGAL_ONBOARDING_REQUIRED");
    }

    @Test
    void completeOnboarding_storesAcceptancesAndMarksUserComplete() {
        User user = user(10L, false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        stubCurrent();
        service.completeOnboarding(10L, new LegalOnboardingRequest(3L, 2L, false, false));
        assertThat(user.isLegalOnboardingCompleted()).isTrue();
        verify(acceptanceRepository, times(2)).save(any());
        assertThatCode(() -> service.requireCompleted(user)).doesNotThrowAnyException();
    }

    @Test
    void validateCheckout_checkboxFalse_rejectedBeforeDocumentLookup() {
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.validateCheckout(checkoutRequest(false, 6L, 7L, 8L)));
        assertThat(ex.getErrorCode()).isEqualTo("REQUIRED_CHECKOUT_LEGAL_ACCEPTANCE_MISSING");
        verifyNoInteractions(documentService);
    }

    @Test
    void validateCheckout_missingDocumentId_rejected() {
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.validateCheckout(checkoutRequest(true, 6L, null, 8L)));
        assertThat(ex.getErrorCode()).isEqualTo("REQUIRED_CHECKOUT_LEGAL_ACCEPTANCE_MISSING");
        verifyNoInteractions(documentService);
    }

    @Test
    void validateCheckout_wrongDocumentType_rejected() {
        when(documentService.findById(6L)).thenReturn(distanceSales);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.validateCheckout(checkoutRequest(true, 6L, 7L, 8L)));
        assertThat(ex.getErrorCode()).isEqualTo("CHECKOUT_LEGAL_DOCUMENT_TYPE_MISMATCH");
        verify(documentService, never()).currentEntity(any());
    }

    @Test
    void validateCheckout_staleDocument_rejected() {
        LegalDocument stalePreInformation = document(60L, LegalDocumentType.PRE_INFORMATION_FORM);
        stubSubmittedCheckout(stalePreInformation, distanceSales, refundPolicy);
        stubCurrentCheckout();
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.validateCheckout(checkoutRequest(true, 60L, 7L, 8L)));
        assertThat(ex.getErrorCode()).isEqualTo("CHECKOUT_LEGAL_DOCUMENT_NOT_CURRENT");
    }

    @Test
    void validateCheckout_retiredDocument_rejected() {
        LegalDocument retired = document(61L, LegalDocumentType.PRE_INFORMATION_FORM);
        retired.setStatus(LegalDocumentStatus.RETIRED);
        stubSubmittedCheckout(retired, distanceSales, refundPolicy);
        stubCurrentCheckout();
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.validateCheckout(checkoutRequest(true, 61L, 7L, 8L)));
        assertThat(ex.getErrorCode()).isEqualTo("CHECKOUT_LEGAL_DOCUMENT_NOT_CURRENT");
    }

    @Test
    void validateCheckout_futureEffectiveDocument_rejected() {
        LegalDocument future = document(62L, LegalDocumentType.PRE_INFORMATION_FORM);
        future.setStatus(LegalDocumentStatus.DRAFT);
        future.setEffectiveAt(Instant.now().plusSeconds(3600));
        stubSubmittedCheckout(future, distanceSales, refundPolicy);
        stubCurrentCheckout();
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.validateCheckout(checkoutRequest(true, 62L, 7L, 8L)));
        assertThat(ex.getErrorCode()).isEqualTo("CHECKOUT_LEGAL_DOCUMENT_NOT_CURRENT");
    }

    @Test
    void validateCheckout_unknownDocument_preservesNotFoundError() {
        when(documentService.findById(99L)).thenThrow(new ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND, "LEGAL_DOCUMENT_NOT_FOUND", "Bulunamadı"));
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.validateCheckout(checkoutRequest(true, 99L, 7L, 8L)));
        assertThat(ex.getErrorCode()).isEqualTo("LEGAL_DOCUMENT_NOT_FOUND");
    }

    @Test
    void validateCheckout_allCurrent_succeeds() {
        stubSubmittedCheckout(preInformation, distanceSales, refundPolicy);
        stubCurrentCheckout();
        var result = service.validateCheckout(checkoutRequest(true, 6L, 7L, 8L));
        assertThat(result.preInformation()).isSameAs(preInformation);
        assertThat(result.distanceSales()).isSameAs(distanceSales);
        assertThat(result.refundCancellationPolicy()).isSameAs(refundPolicy);
    }

    @Test
    void recordCheckout_createsThreeSeparateSnapshottedTransactionLinkedRecords() {
        User user = user(10L, true);
        Subscription subscription = new Subscription();
        ReflectionTestUtils.setField(subscription, "id", 20L);
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 30L);
        var documents = new LegalAcceptanceService.CheckoutDocuments(
                preInformation, distanceSales, refundPolicy);

        service.recordCheckoutAcceptances(user, subscription, payment, documents);

        ArgumentCaptor<LegalAcceptance> captor = ArgumentCaptor.forClass(LegalAcceptance.class);
        verify(acceptanceRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(LegalAcceptance::getDocumentType)
                .containsExactlyInAnyOrder(LegalDocumentType.PRE_INFORMATION_FORM,
                        LegalDocumentType.DISTANCE_SALES_AGREEMENT,
                        LegalDocumentType.REFUND_CANCELLATION_POLICY);
        assertThat(captor.getAllValues()).allSatisfy(acceptance -> {
            assertThat(acceptance.getDocumentVersion()).isEqualTo("1.0");
            assertThat(acceptance.getDocumentContentHash()).isEqualTo("a".repeat(64));
            assertThat(acceptance.getSubscription()).isSameAs(subscription);
            assertThat(acceptance.getPayment()).isSameAs(payment);
            assertThat(acceptance.getSource()).isEqualTo("SUBSCRIPTION_CHECKOUT");
        });
    }

    @Test
    void recordCheckout_samePendingAttempt_isIdempotent() {
        User user = user(10L, true);
        Subscription subscription = new Subscription();
        ReflectionTestUtils.setField(subscription, "id", 20L);
        Payment payment = new Payment();
        ReflectionTestUtils.setField(payment, "id", 30L);
        when(acceptanceRepository
                .existsBySubscriptionIdAndLegalDocumentIdAndAcceptanceTypeAndSourceAndWithdrawnAtIsNull(
                        any(), any(), any(), any())).thenReturn(true);

        service.recordCheckoutAcceptances(user, subscription, payment,
                new LegalAcceptanceService.CheckoutDocuments(preInformation, distanceSales, refundPolicy));

        verify(acceptanceRepository, never()).save(any());
    }

    private void stubCurrent() {
        when(documentService.currentEntity(LegalDocumentType.TERMS_OF_USE)).thenReturn(terms);
        when(documentService.currentEntity(LegalDocumentType.EXPLICIT_CONSENT)).thenReturn(explicit);
        when(documentService.findById(3L)).thenReturn(terms);
        when(documentService.findById(2L)).thenReturn(explicit);
    }

    private void stubSubmittedCheckout(LegalDocument pre, LegalDocument distance, LegalDocument refund) {
        when(documentService.findById(pre.getId())).thenReturn(pre);
        when(documentService.findById(distance.getId())).thenReturn(distance);
        when(documentService.findById(refund.getId())).thenReturn(refund);
    }

    private void stubCurrentCheckout() {
        when(documentService.currentEntity(LegalDocumentType.PRE_INFORMATION_FORM)).thenReturn(preInformation);
        when(documentService.currentEntity(LegalDocumentType.DISTANCE_SALES_AGREEMENT)).thenReturn(distanceSales);
        when(documentService.currentEntity(LegalDocumentType.REFUND_CANCELLATION_POLICY)).thenReturn(refundPolicy);
    }

    private SubscriptionCheckoutRequest checkoutRequest(boolean accepted, Long pre, Long distance, Long refund) {
        return new SubscriptionCheckoutRequest(7L, 3L, pre, distance, refund, accepted);
    }

    private LegalDocument document(Long id, LegalDocumentType type) {
        LegalDocument document = new LegalDocument();
        ReflectionTestUtils.setField(document, "id", id);
        document.setType(type);
        document.setDocumentVersion("1.0");
        document.setContentHash("a".repeat(64));
        return document;
    }

    private User user(Long id, boolean completed) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setLegalOnboardingCompleted(completed);
        return user;
    }
}
