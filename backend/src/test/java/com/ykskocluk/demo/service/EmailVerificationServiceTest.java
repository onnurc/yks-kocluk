package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.EmailVerificationProperties;
import com.ykskocluk.demo.entity.EmailVerificationCode;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.EmailVerificationCodeRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock EmailVerificationCodeRepository codeRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private EmailVerificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(codeRepository, userRepository, new BCryptPasswordEncoder(4),
                eventPublisher, new EmailVerificationProperties(Duration.ofMinutes(10), Duration.ofSeconds(60),
                Duration.ofDays(7), 5));
        user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("student@example.com");
        lenient().when(codeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void issueStoresOnlyHashAndPublishesSixDigitCode() {
        service.issueForRegistration(user);

        ArgumentCaptor<EmailVerificationCode> codeCaptor = ArgumentCaptor.forClass(EmailVerificationCode.class);
        verify(codeRepository).save(codeCaptor.capture());
        ArgumentCaptor<EmailVerificationMailEvent> eventCaptor = ArgumentCaptor.forClass(EmailVerificationMailEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        String raw = eventCaptor.getValue().code();
        assertThat(raw).matches("^[0-9]{6}$");
        assertThat(codeCaptor.getValue().getCodeHash()).isNotEqualTo(raw);
        assertThat(new BCryptPasswordEncoder().matches(raw, codeCaptor.getValue().getCodeHash())).isTrue();
        assertThat(codeCaptor.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(590));
        verify(codeRepository).invalidateAllForUser(eq(7L), any());
    }

    @Test
    void correctCodeVerifiesAndInvalidatesEveryOutstandingCode() {
        String raw = "123456";
        EmailVerificationCode stored = activeCode(raw);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(codeRepository.findLatestForUpdate(7L)).thenReturn(Optional.of(stored));

        assertThat(service.verify(7L, raw).emailVerified()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        verify(codeRepository).invalidateAllForUser(eq(7L), any());
        verify(eventPublisher).publishEvent(new WelcomeMailEvent("student@example.com", user.getFullName()));
    }

    @Test
    void invalidAndExpiredCodesHaveDistinctErrors() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        EmailVerificationCode invalid = activeCode("123456");
        when(codeRepository.findLatestForUpdate(7L)).thenReturn(Optional.of(invalid));
        ApiException invalidError = catchThrowableOfType(ApiException.class, () -> service.verify(7L, "654321"));
        assertThat(invalidError.getErrorCode()).isEqualTo("EMAIL_VERIFICATION_CODE_INVALID");
        assertThat(invalid.getAttemptCount()).isEqualTo(1);

        EmailVerificationCode expired = activeCode("123456");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(codeRepository.findLatestForUpdate(7L)).thenReturn(Optional.of(expired));
        ApiException expiredError = catchThrowableOfType(ApiException.class, () -> service.verify(7L, "123456"));
        assertThat(expiredError.getErrorCode()).isEqualTo("EMAIL_VERIFICATION_CODE_EXPIRED");
    }

    @Test
    void verifiedUserIsIdempotentAndDoesNotCreateOrSendCode() {
        user.setEmailVerified(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThat(service.verify(7L, "anything").emailVerified()).isTrue();
        assertThat(service.resend(7L).emailVerified()).isTrue();
        verify(codeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resendBeforeCooldownIsRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        EmailVerificationCode latest = activeCode("123456");
        ReflectionTestUtils.setField(latest, "createdAt", Instant.now());
        when(codeRepository.findFirstByUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(latest));

        ApiException error = catchThrowableOfType(ApiException.class, () -> service.resend(7L));
        assertThat(error.getErrorCode()).isEqualTo("EMAIL_VERIFICATION_RESEND_TOO_SOON");
        assertThat(error.getProperties()).containsKey("nextAllowedAt");
    }

    private EmailVerificationCode activeCode(String raw) {
        EmailVerificationCode code = new EmailVerificationCode();
        code.setUser(user);
        code.setCodeHash(new BCryptPasswordEncoder(4).encode(raw));
        code.setExpiresAt(Instant.now().plusSeconds(600));
        return code;
    }
}
