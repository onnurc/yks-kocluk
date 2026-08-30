package com.ykskocluk.demo;

import com.ykskocluk.demo.entity.EmailVerificationCode;
import com.ykskocluk.demo.entity.PasswordResetToken;
import com.ykskocluk.demo.entity.RefreshToken;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.AccountOrigin;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.EmailVerificationCodeRepository;
import com.ykskocluk.demo.repository.PasswordResetTokenRepository;
import com.ykskocluk.demo.repository.RefreshTokenRepository;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.service.UnverifiedRegistrationCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UnverifiedRegistrationCleanupIntegrationTest {
    @Autowired UserRepository users;
    @Autowired EmailVerificationCodeRepository verificationCodes;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UnverifiedRegistrationCleanupService cleanupService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void stalePublicRegistrationArtifactsAreDeletedAndEmailCanBeReused() {
        User stale = user("cleanup-reuse@example.com", Role.STUDENT, AccountOrigin.PUBLIC_PASSWORD, false);
        users.saveAndFlush(stale);

        EmailVerificationCode code = new EmailVerificationCode();
        code.setUser(stale); code.setCodeHash("hash"); code.setExpiresAt(Instant.now().plusSeconds(600));
        verificationCodes.save(code);
        PasswordResetToken reset = new PasswordResetToken();
        reset.setUser(stale); reset.setTokenHash("a".repeat(64)); reset.setExpiresAt(Instant.now().plusSeconds(600));
        resetTokens.save(reset);
        RefreshToken refresh = new RefreshToken();
        refresh.setUser(stale); refresh.setTokenHash("b".repeat(64)); refresh.setExpiresAt(Instant.now().plusSeconds(600));
        refreshTokens.save(refresh);
        jdbc.update("update users set created_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(25 * 3600)), stale.getId());

        assertThat(cleanupService.cleanup()).isEqualTo(1);
        assertThat(users.findById(stale.getId())).isEmpty();

        User replacement = users.saveAndFlush(
                user("cleanup-reuse@example.com", Role.STUDENT, AccountOrigin.PUBLIC_PASSWORD, false));
        assertThat(replacement.getId()).isNotNull();
        users.delete(replacement);
    }

    @Test
    void oldLegitimateOriginsRemainEvenWhenUnverified() {
        User oauth = users.saveAndFlush(user("cleanup-oauth@example.com", Role.STUDENT, AccountOrigin.OAUTH, false));
        User admin = users.saveAndFlush(user("cleanup-admin@example.com", Role.ADMIN, AccountOrigin.LEGACY, false));
        User manual = users.saveAndFlush(user("cleanup-manual@example.com", Role.COACH, AccountOrigin.ADMIN_MANUAL, false));
        User application = users.saveAndFlush(user("cleanup-application@example.com", Role.COACH, AccountOrigin.COACH_APPLICATION, false));
        for (User user : new User[]{oauth, admin, manual, application}) {
            jdbc.update("update users set created_at = ? where id = ?",
                    java.sql.Timestamp.from(Instant.now().minusSeconds(48 * 3600)), user.getId());
        }

        cleanupService.cleanup();

        assertThat(users.findById(oauth.getId())).isPresent();
        assertThat(users.findById(admin.getId())).isPresent();
        assertThat(users.findById(manual.getId())).isPresent();
        assertThat(users.findById(application.getId())).isPresent();
        users.deleteAll(java.util.List.of(oauth, admin, manual, application));
    }

    private User user(String email, Role role, AccountOrigin origin, boolean verified) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-a-real-password-hash");
        user.setFullName("Cleanup Test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(verified);
        user.setLegalOnboardingCompleted(true);
        user.setAccountOrigin(origin);
        return user;
    }
}
