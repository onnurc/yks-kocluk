package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.AccountOrigin;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.repository.ConsentRecordRepository;
import com.ykskocluk.demo.repository.EmailVerificationCodeRepository;
import com.ykskocluk.demo.repository.LegalAcceptanceRepository;
import com.ykskocluk.demo.repository.MarketingPreferenceRepository;
import com.ykskocluk.demo.repository.PasswordResetTokenRepository;
import com.ykskocluk.demo.repository.PrivacyPreferenceRepository;
import com.ykskocluk.demo.repository.RefreshTokenRepository;
import com.ykskocluk.demo.repository.StudentProfileRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnverifiedRegistrationCleanupServiceTest {
    @Mock UserRepository users;
    @Mock EmailVerificationCodeRepository verificationCodes;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock StudentProfileRepository studentProfiles;
    @Mock LegalAcceptanceRepository legalAcceptances;
    @Mock MarketingPreferenceRepository marketingPreferences;
    @Mock PrivacyPreferenceRepository privacyPreferences;
    @Mock ConsentRecordRepository consentRecords;
    private UnverifiedRegistrationCleanupService service;

    @BeforeEach
    void setUp() {
        service = new UnverifiedRegistrationCleanupService(users, verificationCodes, resetTokens, refreshTokens,
                studentProfiles, legalAcceptances, marketingPreferences, privacyPreferences, consentRecords);
    }

    @Test
    void staleUnverifiedPublicRegistrationAndArtifactsAreDeleted() {
        User user = user(AccountOrigin.PUBLIC_PASSWORD, Role.STUDENT, false, Instant.now().minusSeconds(25 * 3600));
        candidates(user);

        assertThat(service.cleanup()).isEqualTo(1);

        Long id = user.getId();
        verify(verificationCodes).deleteByUserId(id);
        verify(resetTokens).deleteByUserId(id);
        verify(refreshTokens).deleteByUserId(id);
        verify(consentRecords).deleteByUserId(id);
        verify(privacyPreferences).deleteByUserId(id);
        verify(marketingPreferences).deleteByUserId(id);
        verify(legalAcceptances).deleteByUserId(id);
        verify(studentProfiles).deleteByUserId(id);
        verify(users).delete(user);
    }

    @Test
    void youngerOrVerifiedPublicRegistrationsAreRetainedAfterLockedRecheck() {
        User young = user(AccountOrigin.PUBLIC_PASSWORD, Role.STUDENT, false, Instant.now().minusSeconds(23 * 3600));
        User verified = user(AccountOrigin.PUBLIC_PASSWORD, Role.STUDENT, true, Instant.now().minusSeconds(30 * 3600));
        when(users.findUnverifiedPublicRegistrationIds(any(), any(Pageable.class)))
                .thenReturn(List.of(young.getId(), verified.getId()));
        when(users.findByIdForUpdate(young.getId())).thenReturn(Optional.of(young));
        when(users.findByIdForUpdate(verified.getId())).thenReturn(Optional.of(verified));

        assertThat(service.cleanup()).isZero();
        verify(users, never()).delete(any());
    }

    @Test
    void oauthAdminAndBothCoachProvisioningOriginsAreNeverDeleted() {
        User oauth = user(AccountOrigin.OAUTH, Role.STUDENT, false, Instant.now().minusSeconds(48 * 3600));
        User admin = user(AccountOrigin.LEGACY, Role.ADMIN, false, Instant.now().minusSeconds(48 * 3600));
        User manualCoach = user(AccountOrigin.ADMIN_MANUAL, Role.COACH, false, Instant.now().minusSeconds(48 * 3600));
        User applicationCoach = user(AccountOrigin.COACH_APPLICATION, Role.COACH, false, Instant.now().minusSeconds(48 * 3600));
        List<User> retained = List.of(oauth, admin, manualCoach, applicationCoach);
        when(users.findUnverifiedPublicRegistrationIds(any(), any(Pageable.class)))
                .thenReturn(retained.stream().map(User::getId).toList());
        retained.forEach(user -> when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user)));

        assertThat(service.cleanup()).isZero();
        verify(users, never()).delete(any());
    }

    private void candidates(User user) {
        when(users.findUnverifiedPublicRegistrationIds(any(), any(Pageable.class))).thenReturn(List.of(user.getId()));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    }

    private User user(AccountOrigin origin, Role role, boolean verified, Instant createdAt) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", Math.abs((long) origin.ordinal() * 10 + role.ordinal() + (verified ? 100 : 1)));
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        user.setAccountOrigin(origin);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(verified);
        return user;
    }
}
