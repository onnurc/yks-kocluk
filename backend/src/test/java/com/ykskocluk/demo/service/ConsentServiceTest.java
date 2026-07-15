package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.ConsentCreateRequest;
import com.ykskocluk.demo.dto.ConsentResponse;
import com.ykskocluk.demo.entity.ConsentRecord;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.ConsentType;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.ConsentRecordRepository;
import com.ykskocluk.demo.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConsentService}.
 */
@ExtendWith(MockitoExtension.class)
class ConsentServiceTest {

    @Mock
    ConsentRecordRepository consentRecordRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ConsentService consentService;

    @Test
    void recordConsent_success_savesRecordWithHttpContext() {
        User user = new User();
        user.setEmail("user@example.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        ConsentCreateRequest request = new ConsentCreateRequest(ConsentType.KVKK, "v1.0");

        ConsentResponse response = consentService.recordConsent(1L, request, httpRequest);

        assertThat(response).isNotNull();
        assertThat(response.consentType()).isEqualTo(ConsentType.KVKK);
        assertThat(response.documentVersion()).isEqualTo("v1.0");
        assertThat(response.acceptedAt()).isNotNull();

        verify(consentRecordRepository).saveAndFlush(argThat(r ->
                r.getUser() == user &&
                r.getConsentType() == ConsentType.KVKK &&
                r.getDocumentVersion().equals("v1.0") &&
                r.getIpAddress().equals("192.168.1.1") &&
                r.getUserAgent().equals("Mozilla/5.0")
        ));
    }

    @Test
    void recordConsent_userNotFound_throwsNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        ConsentCreateRequest request = new ConsentCreateRequest(ConsentType.KVKK, "v1.0");

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> consentService.recordConsent(1L, request, null));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("USER_NOT_FOUND");

        verify(consentRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void recordConsent_invalidVersion_throwsBadRequest() {
        User user = new User();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ConsentCreateRequest request = new ConsentCreateRequest(ConsentType.KVKK, "v2.0");

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> consentService.recordConsent(1L, request, null));

        assertThat(ex).isNotNull();
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_DOCUMENT_VERSION");
    }

    @Test
    void getConsentStatus_success() {
        ConsentRecord record = new ConsentRecord();
        record.setDocumentVersion("v1.0");
        record.setStatus(com.ykskocluk.demo.enums.ConsentStatus.ACCEPTED);

        when(consentRecordRepository.findFirstByUserIdAndConsentTypeOrderByAcceptedAtDesc(1L, ConsentType.KVKK))
                .thenReturn(Optional.of(record));

        var status = consentService.getConsentStatus(1L, ConsentType.KVKK);

        assertThat(status.currentVersion()).isEqualTo("v1.0");
        assertThat(status.hasConsented()).isTrue();
    }
}
