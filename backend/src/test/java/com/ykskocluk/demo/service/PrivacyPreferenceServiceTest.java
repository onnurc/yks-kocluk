package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.PrivacyPreferencesUpdateRequest;
import com.ykskocluk.demo.entity.LegalDocument;
import com.ykskocluk.demo.entity.PrivacyPreference;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.LegalDocumentType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.PrivacyPreferenceRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivacyPreferenceServiceTest {
    @Mock PrivacyPreferenceRepository repository;
    @Mock UserRepository userRepository;
    @Mock LegalDocumentService documentService;
    PrivacyPreferenceService service;
    User user;
    LegalDocument current;

    @BeforeEach
    void setUp() {
        service = new PrivacyPreferenceService(repository, userRepository, documentService);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        current = policy(5L, LegalDocumentType.COOKIE_POLICY);
        lenient().when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        lenient().when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void noStoredPreference_defaultsOptionalCategoriesToFalse() {
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());
        var response = service.get(7L);
        assertThat(response.necessaryAllowed()).isTrue();
        assertThat(response.analyticsAllowed()).isFalse();
        assertThat(response.marketingAllowed()).isFalse();
    }

    @Test
    void necessaryStorageCannotBeDisabled() {
        ApiException error = catchThrowableOfType(ApiException.class, () -> service.update(7L,
                new PrivacyPreferencesUpdateRequest(false, false, false, 5L)));
        assertThat(error.getErrorCode()).isEqualTo("NECESSARY_PREFERENCE_REQUIRED");
        verifyNoInteractions(documentService);
    }

    @Test
    void currentCookiePolicyRequiredAndOptionsUpdateIndependently() {
        when(documentService.findById(5L)).thenReturn(current);
        when(documentService.currentEntity(LegalDocumentType.COOKIE_POLICY)).thenReturn(current);
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());

        var response = service.update(7L, new PrivacyPreferencesUpdateRequest(null, true, false, 5L));

        assertThat(response.necessaryAllowed()).isTrue();
        assertThat(response.analyticsAllowed()).isTrue();
        assertThat(response.marketingAllowed()).isFalse();
        assertThat(response.policyVersion()).isEqualTo("1.0");
    }

    @Test
    void staleFutureRetiredOrWrongTypeDocumentIsRejectedByCurrentIdentity() {
        LegalDocument stale = policy(4L, LegalDocumentType.COOKIE_POLICY);
        when(documentService.findById(4L)).thenReturn(stale);
        when(documentService.currentEntity(LegalDocumentType.COOKIE_POLICY)).thenReturn(current);

        ApiException error = catchThrowableOfType(ApiException.class, () -> service.update(7L,
                new PrivacyPreferencesUpdateRequest(null, true, null, 4L)));

        assertThat(error.getErrorCode()).isEqualTo("PRIVACY_PREFERENCE_DOCUMENT_NOT_CURRENT");
        verify(repository, never()).save(any());
    }

    @Test
    void identicalUpdateDoesNotWrite() {
        PrivacyPreference preference = new PrivacyPreference();
        ReflectionTestUtils.setField(preference, "id", 9L);
        preference.setUser(user);
        preference.setAnalyticsAllowed(true);
        preference.setMarketingAllowed(false);
        preference.setCookiePolicyDocument(current);
        preference.setPolicyVersion("1.0");
        preference.setGrantedAt(Instant.now());
        when(documentService.findById(5L)).thenReturn(current);
        when(documentService.currentEntity(LegalDocumentType.COOKIE_POLICY)).thenReturn(current);
        when(repository.findByUserId(7L)).thenReturn(Optional.of(preference));

        service.update(7L, new PrivacyPreferencesUpdateRequest(null, true, false, 5L));

        verify(repository, never()).save(any());
    }

    private LegalDocument policy(Long id, LegalDocumentType type) {
        LegalDocument document = new LegalDocument();
        ReflectionTestUtils.setField(document, "id", id);
        document.setType(type);
        document.setDocumentVersion("1.0");
        return document;
    }
}
