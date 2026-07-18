package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.OAuth2LoginCode;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.OAuth2LoginCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginCodeServiceTest {

    @Mock
    private OAuth2LoginCodeRepository repository;

    private Clock clock;
    private OAuth2LoginCodeService service;
    private User testUser;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneId.of("UTC"));
        service = new OAuth2LoginCodeService(repository, clock, 120);

        testUser = new User();
        testUser.setEmail("test@example.com");
    }

    @Test
    void generateCodeForUser_savesHashedCodeAndReturnsRawCode() {
        String rawCode = service.generateCodeForUser(testUser);

        assertThat(rawCode).isNotBlank();
        assertThat(rawCode.length()).isEqualTo(43); // 32 bytes base64 without padding

        ArgumentCaptor<OAuth2LoginCode> captor = ArgumentCaptor.forClass(OAuth2LoginCode.class);
        verify(repository).save(captor.capture());

        OAuth2LoginCode saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(testUser);
        assertThat(saved.getCodeHash()).isNotEqualTo(rawCode);
        assertThat(saved.getExpiresAt()).isEqualTo(Instant.parse("2026-07-18T12:02:00Z")); // +120s
    }

    @Test
    void consumeCode_validCode_success() {
        OAuth2LoginCode code = new OAuth2LoginCode();
        code.setUser(testUser);
        code.setExpiresAt(Instant.parse("2026-07-18T12:02:00Z"));

        when(repository.findByCodeHash(anyString())).thenReturn(Optional.of(code));
        when(repository.consumeCodeAtomically(anyString(), any(Instant.class))).thenReturn(1);

        User consumed = service.consumeCode("some-raw-code");

        assertThat(consumed).isEqualTo(testUser);
        verify(repository).consumeCodeAtomically(anyString(), eq(Instant.now(clock)));
    }

    @Test
    void consumeCode_unknownCode_throwsUnauthorized() {
        when(repository.findByCodeHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeCode("unknown-code"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Geçersiz veya süresi dolmuş kod")
                .extracting("errorCode")
                .isEqualTo("INVALID_OAUTH_CODE");
    }

    @Test
    void consumeCode_alreadyConsumedOrExpired_throwsUnauthorized() {
        OAuth2LoginCode code = new OAuth2LoginCode();
        code.setUser(testUser);
        code.setExpiresAt(Instant.parse("2026-07-18T12:02:00Z"));

        when(repository.findByCodeHash(anyString())).thenReturn(Optional.of(code));
        // Atomic consume returns 0 (either consumed or expired)
        when(repository.consumeCodeAtomically(anyString(), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.consumeCode("expired-code"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Geçersiz veya süresi dolmuş kod")
                .extracting("errorCode")
                .isEqualTo("INVALID_OAUTH_CODE");
    }
}
