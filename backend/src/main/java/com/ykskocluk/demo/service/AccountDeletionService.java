package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.AccountDeletionRequestRequest;
import com.ykskocluk.demo.dto.AccountDeletionResponse;
import com.ykskocluk.demo.entity.AccountDeletionRequest;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.AccountDeletionStatus;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AccountDeletionService {
    private static final String CONFIRMATION = "DELETE";

    private final AccountDeletionRequestRepository deletionRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OAuth2LoginCodeRepository oauth2LoginCodeRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final MarketingPreferenceService marketingPreferenceService;
    private final PrivacyPreferenceService privacyPreferenceService;
    private final MediaService mediaService;

    public AccountDeletionService(AccountDeletionRequestRepository deletionRepository, UserRepository userRepository,
                                  RefreshTokenRepository refreshTokenRepository,
                                  OAuth2LoginCodeRepository oauth2LoginCodeRepository,
                                  StudentProfileRepository studentProfileRepository,
                                   CoachProfileRepository coachProfileRepository,
                                   MarketingPreferenceService marketingPreferenceService,
                                   PrivacyPreferenceService privacyPreferenceService,
                                   MediaService mediaService) {
        this.deletionRepository = deletionRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.oauth2LoginCodeRepository = oauth2LoginCodeRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.coachProfileRepository = coachProfileRepository;
        this.marketingPreferenceService = marketingPreferenceService;
        this.privacyPreferenceService = privacyPreferenceService;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public AccountDeletionResponse get(Long userId) {
        AccountDeletionRequest request = deletionRepository.findByUserId(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_DELETION_NOT_REQUESTED",
                        "Hesap silme talebi bulunamadı"));
        return response(request);
    }

    @Transactional
    public AccountDeletionResponse requestAndComplete(Long userId, AccountDeletionRequestRequest input) {
        if (input == null || !CONFIRMATION.equals(input.confirmation())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_DELETION_CONFIRMATION_INVALID",
                    "Hesap silme işlemini onaylamak için DELETE yazın");
        }
        AccountDeletionRequest existing = deletionRepository.findByUserId(userId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == AccountDeletionStatus.COMPLETED) return response(existing);
            if (existing.getStatus() != AccountDeletionStatus.CANCELLED
                    && existing.getStatus() != AccountDeletionStatus.FAILED) {
                return response(existing);
            }
        }

        User user = userRepository.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Kullanıcı bulunamadı"));
        Instant now = Instant.now();
        AccountDeletionRequest request = existing != null ? existing : new AccountDeletionRequest();
        if (existing == null) request.setUser(user);
        request.setStatus(AccountDeletionStatus.REQUESTED);
        request.setRequestedAt(now);
        request.setCompletedAt(null);
        request.setFailureReason(null);
        request.setProcessingSource("SELF_SERVICE_SYNC");
        request.setIdentityEmailHash(identityHash(user.getEmail()));
        request.setOauthSubjectHash(user.getGoogleSub() == null ? null : identityHash(user.getGoogleSub()));
        deletionRepository.save(request);

        request.setStatus(AccountDeletionStatus.PROCESSING);
        // Blocking status is set before PII mutation. The transaction makes this all-or-nothing.
        user.setStatus(UserStatus.DELETED);
        try {
            refreshTokenRepository.revokeAllForUser(userId, now);
            oauth2LoginCodeRepository.consumeAllForUser(userId, now);
            marketingPreferenceService.withdrawAll(user, now);
            privacyPreferenceService.clearForDeletion(user);
            mediaService.retireAllOwnedBy(userId);
            anonymizeProfiles(userId);
            anonymizeUser(user);
            request.setStatus(AccountDeletionStatus.COMPLETED);
            request.setCompletedAt(now);
        } catch (RuntimeException processingFailure) {
            // Do not copy exception messages: persistence/provider errors can contain PII.
            // If the transaction remains committable, retain a blocked account and a safe
            // operator-visible recovery marker. Fatal DB errors may still roll back atomically.
            user.setStatus(UserStatus.DELETED);
            user.setLegalOnboardingCompleted(false);
            request.setStatus(AccountDeletionStatus.FAILED);
            request.setFailureReason("Deletion processing requires manual remediation");
        }
        return response(request);
    }

    private void anonymizeProfiles(Long userId) {
        studentProfileRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setGradeLevel(null);
            profile.setCity(null);
        });
        coachProfileRepository.findByUserId(userId).ifPresent(profile -> {
            profile.setHeadline("Deleted coach");
            profile.setBio(null);
            profile.setUniversity(null);
            profile.setDepartment(null);
            profile.setGraduationYear(null);
            profile.setStatus(CoachProfileStatus.REJECTED);
            profile.setRejectionReason(null);
            profile.setActiveStudentCount(0);
            profile.setMaxStudentCapacity(0);
            profile.setPayoutAccountReady(false);
        });
    }

    private void anonymizeUser(User user) {
        user.setEmail("deleted+" + user.getId() + "@uniform.invalid");
        user.setPasswordHash(null);
        user.setFullName("Deleted User");
        user.setGoogleSub(null);
        user.setEmailVerified(false);
        user.setSuspensionReason(null);
        user.setDateOfBirth(null);
        user.setLegalOnboardingCompleted(false);
    }

    private AccountDeletionResponse response(AccountDeletionRequest request) {
        return new AccountDeletionResponse(request.getStatus(), request.getRequestedAt(), request.getCompletedAt());
    }

    public static String identityHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
