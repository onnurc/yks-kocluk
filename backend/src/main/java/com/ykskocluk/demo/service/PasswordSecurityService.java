package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.PasswordSecurityProperties;
import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.entity.PasswordResetToken;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.PasswordResetTokenRepository;
import com.ykskocluk.demo.repository.RefreshTokenRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.PasswordPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PasswordSecurityService {
    public static final String GENERIC_FORGOT_MESSAGE = "Bu e-posta adresiyle eşleşen bir hesap varsa şifre sıfırlama bağlantısı gönderildi.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final PasswordSecurityProperties properties;
    private final ApplicationEventPublisher events;

    public PasswordSecurityService(UserRepository users, PasswordResetTokenRepository resetTokens,
                                   RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                                   PasswordSecurityProperties properties, ApplicationEventPublisher events) {
        this.users = users; this.resetTokens = resetTokens; this.refreshTokens = refreshTokens;
        this.encoder = encoder; this.properties = properties; this.events = events;
    }

    @Transactional
    public PasswordActionResponse changePassword(Long userId, ChangePasswordRequest request) {
        validatePassword(request.newPassword());
        User user = users.findById(userId).orElseThrow(() -> unavailable("PASSWORD_CHANGE_NOT_AVAILABLE"));
        ensureEligible(user, "PASSWORD_CHANGE_NOT_AVAILABLE");
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Mevcut şifre hatalı");
        }
        rejectReuse(user, request.newPassword());
        Instant now = now();
        updatePassword(user, request.newPassword(), now);
        invalidateSessionsAndResets(userId, now);
        return new PasswordActionResponse("Şifreniz değiştirildi. Tüm oturumlar kapatıldı; yeniden giriş yapın.", true);
    }

    @Transactional
    public PasswordActionResponse forgotPassword(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        users.findByEmailIgnoreCase(email).filter(this::eligible).ifPresent(user -> {
            Instant now = now();
            resetTokens.invalidateAllForUser(user.getId(), now);
            String raw = randomToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user); token.setTokenHash(sha256(raw));
            token.setExpiresAt(now.plus(properties.resetTokenTtl()));
            resetTokens.save(token);
            String link = UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                    .path("/reset-password").queryParam("token", raw).build().toUriString();
            events.publishEvent(new PasswordResetMailEvent(user.getEmail(), link));
        });
        return new PasswordActionResponse(GENERIC_FORGOT_MESSAGE, false);
    }

    @Transactional
    public PasswordActionResponse resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokens.findByTokenHashForUpdate(sha256(request.token()))
                .orElseThrow(this::invalidResetToken);
        Instant now = now();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) throw invalidResetToken();
        User user = token.getUser();
        ensureEligible(user, "PASSWORD_RESET_TOKEN_INVALID");
        validatePassword(request.newPassword());
        rejectReuse(user, request.newPassword());
        updatePassword(user, request.newPassword(), now);
        token.setUsedAt(now);
        resetTokens.invalidateAllForUser(user.getId(), now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        return new PasswordActionResponse("Şifreniz başarıyla yenilendi. Yeni şifrenizle giriş yapabilirsiniz.", true);
    }

    @Transactional
    @Scheduled(cron = "${app.password-security.cleanup-cron:0 20 3 * * *}", zone = "UTC")
    public int cleanupRetiredTokens() {
        Instant now = now();
        return resetTokens.deleteRetired(now, now.minus(properties.tokenRetention()));
    }

    private void updatePassword(User user, String raw, Instant now) {
        user.setPasswordHash(encoder.encode(raw));
        user.setPasswordChangedAt(now);
        user.setPasswordVersion(user.getPasswordVersion() + 1);
        users.save(user);
    }
    private void invalidateSessionsAndResets(Long id, Instant now) {
        refreshTokens.revokeAllForUser(id, now);
        resetTokens.invalidateAllForUser(id, now);
    }
    private void rejectReuse(User user, String raw) {
        if (encoder.matches(raw, user.getPasswordHash()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_REUSE_NOT_ALLOWED", "Yeni şifre mevcut şifreyle aynı olamaz");
    }
    private void validatePassword(String raw) {
        PasswordPolicy.validate(raw);
    }
    private boolean eligible(User user) { return user.getStatus() == UserStatus.ACTIVE && user.getPasswordHash() != null; }
    private void ensureEligible(User user, String code) { if (!eligible(user)) throw unavailable(code); }
    private ApiException unavailable(String code) { return new ApiException(HttpStatus.BAD_REQUEST, code, "Bu hesap için şifre işlemi kullanılamıyor"); }
    private ApiException invalidResetToken() { return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_INVALID", "Şifre sıfırlama bağlantısı geçersiz veya süresi dolmuş"); }
    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.SECONDS); }
    private static String randomToken() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
