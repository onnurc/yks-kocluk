package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.AccountDeletionRequestRequest;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.enums.AccountDeletionStatus;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import com.ykskocluk.demo.security.WebSocketSessionsInvalidatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {
    @Mock AccountDeletionRequestRepository deletionRepository;
    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock OAuth2LoginCodeRepository oauth2LoginCodeRepository;
    @Mock StudentProfileRepository studentProfileRepository;
    @Mock CoachProfileRepository coachProfileRepository;
    @Mock MarketingPreferenceService marketingPreferenceService;
    @Mock PrivacyPreferenceService privacyPreferenceService;
    @Mock MediaService mediaService;
    @Mock ApplicationEventPublisher eventPublisher;
    AccountDeletionService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(deletionRepository, userRepository, refreshTokenRepository,
                oauth2LoginCodeRepository, studentProfileRepository, coachProfileRepository,
                marketingPreferenceService, privacyPreferenceService, mediaService, eventPublisher);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setEmail("person@example.com");
        user.setPasswordHash("hash");
        user.setFullName("Personal Name");
        user.setGoogleSub("google-subject");
        user.setEmailVerified(true);
        user.setDateOfBirth(java.time.LocalDate.of(2000, 1, 1));
        user.setStatus(UserStatus.ACTIVE);
        user.setLegalOnboardingCompleted(true);
    }

    @Test
    void invalidConfirmationRejectedWithoutMutation() {
        ApiException error = catchThrowableOfType(ApiException.class,
                () -> service.requestAndComplete(7L, new AccountDeletionRequestRequest("delete")));
        assertThat(error.getErrorCode()).isEqualTo("ACCOUNT_DELETION_CONFIRMATION_INVALID");
        verifyNoInteractions(userRepository, refreshTokenRepository);
    }

    @Test
    void completionBlocksAccountRevokesSessionsAndAnonymizesAllAvailableProfilePii() {
        StudentProfile student = new StudentProfile();
        student.setGradeLevel("12");
        student.setCity("Ankara");
        CoachProfile coach = new CoachProfile();
        coach.setHeadline("Top coach");
        coach.setBio("Personal bio");
        coach.setDepartment("Engineering");
        coach.setGraduationYear(2024);
        coach.setStatus(CoachProfileStatus.APPROVED);
        coach.setMaxStudentCapacity(10);
        when(deletionRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(studentProfileRepository.findByUserId(7L)).thenReturn(Optional.of(student));
        when(coachProfileRepository.findByUserId(7L)).thenReturn(Optional.of(coach));

        var response = service.requestAndComplete(7L, new AccountDeletionRequestRequest("DELETE"));

        assertThat(response.status()).isEqualTo(AccountDeletionStatus.COMPLETED);
        assertThat(response.completedAt()).isNotNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getEmail()).isEqualTo("deleted+7@uniform.invalid");
        assertThat(user.getFullName()).isEqualTo("Deleted User");
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getGoogleSub()).isNull();
        assertThat(user.getDateOfBirth()).isNull();
        assertThat(user.isLegalOnboardingCompleted()).isFalse();
        assertThat(student.getGradeLevel()).isNull();
        assertThat(student.getCity()).isNull();
        assertThat(coach.getStatus()).isEqualTo(CoachProfileStatus.REJECTED);
        assertThat(coach.getBio()).isNull();
        assertThat(coach.getDepartment()).isNull();
        assertThat(coach.getMaxStudentCapacity()).isZero();
        verify(refreshTokenRepository).revokeAllForUser(eq(7L), any());
        verify(oauth2LoginCodeRepository).consumeAllForUser(eq(7L), any());
        verify(marketingPreferenceService).withdrawAll(eq(user), any());
        verify(privacyPreferenceService).clearForDeletion(user);
        verify(mediaService).retireAllOwnedBy(7L);
        verify(deletionRepository, never()).delete(any());
        verify(userRepository, never()).delete(any());
        verify(eventPublisher).publishEvent(new WebSocketSessionsInvalidatedEvent(7L));
    }

    @Test
    void completedRequestIsIdempotentAndDoesNotAnonymizeAgain() {
        AccountDeletionRequest existing = new AccountDeletionRequest();
        existing.setUser(user);
        existing.setStatus(AccountDeletionStatus.COMPLETED);
        existing.setRequestedAt(java.time.Instant.now());
        existing.setCompletedAt(java.time.Instant.now());
        when(deletionRepository.findByUserId(7L)).thenReturn(Optional.of(existing));

        var response = service.requestAndComplete(7L, new AccountDeletionRequestRequest("DELETE"));

        assertThat(response.status()).isEqualTo(AccountDeletionStatus.COMPLETED);
        verifyNoInteractions(userRepository, refreshTokenRepository, oauth2LoginCodeRepository,
                studentProfileRepository, coachProfileRepository, marketingPreferenceService,
                privacyPreferenceService, mediaService);
    }

    @Test
    void processingFailureRecordsSafeFailureAndKeepsAccessBlocked() {
        when(deletionRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.revokeAllForUser(eq(7L), any()))
                .thenThrow(new IllegalStateException("provider detail with person@example.com"));

        var response = service.requestAndComplete(7L, new AccountDeletionRequestRequest("DELETE"));

        assertThat(response.status()).isEqualTo(AccountDeletionStatus.FAILED);
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.isLegalOnboardingCompleted()).isFalse();
        verify(deletionRepository).save(argThat(request ->
                request.getFailureReason() == null || !request.getFailureReason().contains("person@example.com")));
    }
}
