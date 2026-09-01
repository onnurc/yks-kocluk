package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PasswordSecurityProperties;
import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordSecurityServiceTest {
    @Mock UserRepository users; @Mock PasswordResetTokenRepository resetTokens;
    @Mock RefreshTokenRepository refreshTokens; @Mock PasswordEncoder encoder;
    @Mock ApplicationEventPublisher events;
    PasswordSecurityService service;

    @BeforeEach void setup() {
        service = new PasswordSecurityService(users, resetTokens, refreshTokens, encoder,
                new PasswordSecurityProperties("https://app.example.com", Duration.ofMinutes(30), Duration.ofDays(7)), events);
    }
    private User user() { User u=new User(); ReflectionTestUtils.setField(u,"id",1L); u.setEmail("user@example.com"); u.setPasswordHash("old-hash"); u.setStatus(UserStatus.ACTIVE); u.setRole(Role.STUDENT); u.setFullName("User"); return u; }

    @Test void changePassword_succeedsAndRevokesSessionsAndResets() {
        User u=user(); when(users.findById(1L)).thenReturn(Optional.of(u)); when(encoder.matches("old-password","old-hash")).thenReturn(true); when(encoder.matches("new-password","old-hash")).thenReturn(false); when(encoder.encode("new-password")).thenReturn("new-hash");
        assertThat(service.changePassword(1L,new ChangePasswordRequest("old-password","new-password")).reloginRequired()).isTrue();
        assertThat(u.getPasswordHash()).isEqualTo("new-hash"); assertThat(u.getPasswordChangedAt()).isNotNull();
        assertThat(u.getPasswordVersion()).isEqualTo(1);
        verify(refreshTokens).revokeAllForUser(eq(1L),any()); verify(resetTokens).invalidateAllForUser(eq(1L),any());
        verify(events).publishEvent(new com.ykskocluk.demo.security.WebSocketSessionsInvalidatedEvent(1L));
    }
    @Test void changePassword_rejectsIncorrectCurrent() { User u=user(); when(users.findById(1L)).thenReturn(Optional.of(u)); when(encoder.matches("bad-password","old-hash")).thenReturn(false); assertThatThrownBy(() -> service.changePassword(1L,new ChangePasswordRequest("bad-password","new-password"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo("CURRENT_PASSWORD_INVALID")); }
    @Test void changePassword_rejectsReuse() { User u=user(); when(users.findById(1L)).thenReturn(Optional.of(u)); when(encoder.matches(anyString(),eq("old-hash"))).thenReturn(true); assertThatThrownBy(() -> service.changePassword(1L,new ChangePasswordRequest("old-password","old-password"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo("PASSWORD_REUSE_NOT_ALLOWED")); }
    @Test void changePassword_hasNoTimeRestriction() { User u=user(); u.setPasswordChangedAt(Instant.now().minusSeconds(30)); when(users.findById(1L)).thenReturn(Optional.of(u)); when(encoder.matches("old-password","old-hash")).thenReturn(true); when(encoder.matches("new-password","old-hash")).thenReturn(false); when(encoder.encode("new-password")).thenReturn("new-hash"); assertThatCode(() -> service.changePassword(1L,new ChangePasswordRequest("old-password","new-password"))).doesNotThrowAnyException(); }
    @Test void changePassword_googleOnlyUnavailable() { User u=user(); u.setPasswordHash(null); when(users.findById(1L)).thenReturn(Optional.of(u)); assertThatThrownBy(() -> service.changePassword(1L,new ChangePasswordRequest("old-password","new-password"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo("PASSWORD_CHANGE_NOT_AVAILABLE")); }

    @Test void forgotPassword_knownAccountStoresOnlyHashAndPublishesMailEvent() {
        User u=user(); when(users.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(u));
        service.forgotPassword(new ForgotPasswordRequest(" USER@example.com "));
        ArgumentCaptor<PasswordResetToken> token=ArgumentCaptor.forClass(PasswordResetToken.class); verify(resetTokens).save(token.capture());
        assertThat(token.getValue().getTokenHash()).hasSize(64).doesNotContain("http"); assertThat(token.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(1700));
        verify(events).publishEvent(isA(PasswordResetMailEvent.class));
    }
    @Test void forgotPassword_unknownAndGoogleOnlyHaveSameResponseAndNoToken() {
        var unknown=service.forgotPassword(new ForgotPasswordRequest("missing@example.com")); User google=user(); google.setPasswordHash(null); when(users.findByEmailIgnoreCase("google@example.com")).thenReturn(Optional.of(google)); var googleResult=service.forgotPassword(new ForgotPasswordRequest("google@example.com"));
        assertThat(unknown.message()).isEqualTo(googleResult.message()); verify(resetTokens,never()).save(any());
    }

    @Test void resetPassword_validTokenBypassesCooldownAndIsSingleUse() {
        User u=user(); u.setPasswordChangedAt(Instant.now()); PasswordResetToken token=new PasswordResetToken(); token.setUser(u); token.setExpiresAt(Instant.now().plusSeconds(600)); when(resetTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token)); when(encoder.matches("new-password","old-hash")).thenReturn(false); when(encoder.encode("new-password")).thenReturn("new-hash");
        service.resetPassword(new ResetPasswordRequest("raw-token","new-password")); assertThat(token.getUsedAt()).isNotNull(); assertThat(u.getPasswordHash()).isEqualTo("new-hash"); verify(refreshTokens).revokeAllForUser(eq(u.getId()),any()); verify(events).publishEvent(new com.ykskocluk.demo.security.WebSocketSessionsInvalidatedEvent(1L));
    }
    @Test void resetPassword_sameCurrentPasswordIsRejectedWithoutConsumingTokenAndCanBeRetried() {
        User u=user(); PasswordResetToken token=new PasswordResetToken(); token.setUser(u); token.setExpiresAt(Instant.now().plusSeconds(600));
        when(resetTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(token));
        when(encoder.matches("old-password","old-hash")).thenReturn(true);
        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest("raw-token","old-password")))
                .isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo("PASSWORD_REUSE_NOT_ALLOWED"));
        assertThat(token.getUsedAt()).isNull(); assertThat(u.getPasswordHash()).isEqualTo("old-hash");
        verify(refreshTokens, never()).revokeAllForUser(any(), any());
        verify(resetTokens, never()).invalidateAllForUser(any(), any());

        when(encoder.matches("genuinely-new","old-hash")).thenReturn(false);
        when(encoder.encode("genuinely-new")).thenReturn("new-hash");
        service.resetPassword(new ResetPasswordRequest("raw-token","genuinely-new"));
        assertThat(token.getUsedAt()).isNotNull(); assertThat(u.getPasswordHash()).isEqualTo("new-hash");
    }
    @Test void resetPassword_invalidExpiredOrUsedUsesGenericError() {
        when(resetTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty()); assertInvalidReset("unknown");
        PasswordResetToken expired=new PasswordResetToken(); expired.setUser(user()); expired.setExpiresAt(Instant.now().minusSeconds(1)); when(resetTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(expired)); assertInvalidReset("expired");
    }
    private void assertInvalidReset(String token) { assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(token,"new-password"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getErrorCode()).isEqualTo("PASSWORD_RESET_TOKEN_INVALID")); }
}
