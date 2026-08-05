package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.LegalOnboardingRequest;
import com.ykskocluk.demo.entity.LegalAcceptance;
import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.LegalDocumentType;
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
    LegalAcceptanceService service;
    LegalDocument terms;
    LegalDocument explicit;

    @BeforeEach
    void setUp() {
        service = new LegalAcceptanceService(documentService, acceptanceRepository, userRepository);
        terms = document(3L, LegalDocumentType.TERMS_OF_USE);
        explicit = document(2L, LegalDocumentType.EXPLICIT_CONSENT);
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
        verify(acceptanceRepository, times(4)).save(captor.capture());
        List<String> sources = captor.getAllValues().stream().map(LegalAcceptance::getSource).toList();
        assertThat(sources).contains("REGISTRATION", "MARKETING_EMAIL", "MARKETING_SMS");
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

    private void stubCurrent() {
        when(documentService.currentEntity(LegalDocumentType.TERMS_OF_USE)).thenReturn(terms);
        when(documentService.currentEntity(LegalDocumentType.EXPLICIT_CONSENT)).thenReturn(explicit);
        when(documentService.findById(3L)).thenReturn(terms);
        when(documentService.findById(2L)).thenReturn(explicit);
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
