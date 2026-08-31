package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.AccountOrigin;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;

@Service
public class UnverifiedRegistrationCleanupService {
    private static final Logger log = LoggerFactory.getLogger(UnverifiedRegistrationCleanupService.class);
    private static final Duration MAX_UNVERIFIED_AGE = Duration.ofHours(24);
    private static final int BATCH_SIZE = 100;

    private final UserRepository users;
    private final EmailVerificationCodeRepository verificationCodes;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final StudentProfileRepository studentProfiles;
    private final LegalAcceptanceRepository legalAcceptances;
    private final MarketingPreferenceRepository marketingPreferences;
    private final PrivacyPreferenceRepository privacyPreferences;
    private final ConsentRecordRepository consentRecords;
    private Clock clock = Clock.systemUTC();

    public UnverifiedRegistrationCleanupService(UserRepository users,
                                                EmailVerificationCodeRepository verificationCodes,
                                                PasswordResetTokenRepository passwordResetTokens,
                                                RefreshTokenRepository refreshTokens,
                                                StudentProfileRepository studentProfiles,
                                                LegalAcceptanceRepository legalAcceptances,
                                                MarketingPreferenceRepository marketingPreferences,
                                                PrivacyPreferenceRepository privacyPreferences,
                                                ConsentRecordRepository consentRecords) {
        this.users = users;
        this.verificationCodes = verificationCodes;
        this.passwordResetTokens = passwordResetTokens;
        this.refreshTokens = refreshTokens;
        this.studentProfiles = studentProfiles;
        this.legalAcceptances = legalAcceptances;
        this.marketingPreferences = marketingPreferences;
        this.privacyPreferences = privacyPreferences;
        this.consentRecords = consentRecords;
    }

    @Scheduled(cron = "${app.email-verification.unverified-account-cleanup-cron:0 0 * * * *}", zone = "UTC")
    @Transactional
    public int cleanup() {
        Instant cutoff = Instant.now(clock).minus(MAX_UNVERIFIED_AGE);
        int deleted = 0;
        for (Long userId : users.findUnverifiedPublicRegistrationIds(cutoff, PageRequest.of(0, BATCH_SIZE))) {
            User user = users.findByIdForUpdate(userId).orElse(null);
            if (!eligible(user, cutoff)) continue;
            verificationCodes.deleteByUserId(userId);
            passwordResetTokens.deleteByUserId(userId);
            refreshTokens.deleteByUserId(userId);
            consentRecords.deleteByUserId(userId);
            privacyPreferences.deleteByUserId(userId);
            marketingPreferences.deleteByUserId(userId);
            legalAcceptances.deleteByUserId(userId);
            studentProfiles.deleteByUserId(userId);
            users.delete(user);
            deleted++;
        }
        if (deleted > 0) log.info("Deleted {} stale unverified public registrations", deleted);
        return deleted;
    }

    private boolean eligible(User user, Instant cutoff) {
        return user != null
                && user.getAccountOrigin() == AccountOrigin.PUBLIC_PASSWORD
                && !user.isEmailVerified()
                && user.getStatus() == UserStatus.ACTIVE
                && user.getCreatedAt() != null
                && !user.getCreatedAt().isAfter(cutoff);
    }
}
