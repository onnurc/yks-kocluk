package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.CoachProfileCreateRequest;
import com.ykskocluk.demo.dto.CoachProfileResponse;
import com.ykskocluk.demo.dto.CoachProfileUpdateRequest;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.University;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.enums.UserStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.CoachProfileMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.CoachSubjectRepository;
import com.ykskocluk.demo.repository.UniversityRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachProfileServiceTest {

    @Mock CoachProfileRepository coachProfileRepository;
    @Mock CoachSubjectRepository coachSubjectRepository;
    @Mock UserRepository userRepository;
    @Mock UniversityRepository universityRepository;
    @Mock CoachProfileMapper coachProfileMapper;

    CoachProfileService service;

    @BeforeEach
    void setUp() {
        service = new CoachProfileService(coachProfileRepository, coachSubjectRepository,
                userRepository, universityRepository, coachProfileMapper);
        lenient().when(coachProfileMapper.toResponse(any(), any())).thenReturn(
                new CoachProfileResponse(1L, 1L, "Coach", "c@e.com", "h", "b", 5L, "Uni", "dep",
                        2020, CoachProfileStatus.PENDING, null, Set.of(Track.NUMERICAL), 0, 10, false));
    }

    private CoachProfileCreateRequest createRequest() {
        return new CoachProfileCreateRequest("Deneyimli koç", "bio", 5L, "Bilgisayar", 2020,
                Set.of(Track.NUMERICAL, Track.EQUAL_WEIGHT));
    }

    @Test
    void create_setsPendingAndDefaultCapacity() {
        when(coachProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(universityRepository.findById(5L)).thenReturn(Optional.of(new University()));

        service.createOwn(1L, createRequest());

        ArgumentCaptor<CoachProfile> captor = ArgumentCaptor.forClass(CoachProfile.class);
        verify(coachProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CoachProfileStatus.PENDING);
        assertThat(captor.getValue().getMaxStudentCapacity()).isEqualTo(10);
        assertThat(captor.getValue().getActiveStudentCount()).isZero();
    }

    @Test
    void create_pendingProfile_throwsConflict() {
        CoachProfile pending = new CoachProfile();
        pending.setStatus(CoachProfileStatus.PENDING);
        when(coachProfileRepository.findByUserId(1L)).thenReturn(Optional.of(pending));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.createOwn(1L, createRequest()));
        assertThat(ex.getErrorCode()).isEqualTo("PROFILE_ALREADY_EXISTS");
        verify(coachProfileRepository, never()).save(any());
    }

    @Test
    void create_approvedProfile_throwsConflict() {
        CoachProfile approved = new CoachProfile();
        approved.setStatus(CoachProfileStatus.APPROVED);
        when(coachProfileRepository.findByUserId(1L)).thenReturn(Optional.of(approved));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.createOwn(1L, createRequest()));
        assertThat(ex.getErrorCode()).isEqualTo("PROFILE_ALREADY_EXISTS");
        verify(coachProfileRepository, never()).save(any());
    }

    @Test
    void create_rejectedProfile_resubmitsToPending() {
        CoachProfile rejected = new CoachProfile();
        rejected.setStatus(CoachProfileStatus.REJECTED);
        rejected.setRejectionReason("Eksik bilgi");
        when(coachProfileRepository.findByUserId(1L)).thenReturn(Optional.of(rejected));
        when(universityRepository.findById(5L)).thenReturn(Optional.of(new University()));

        service.createOwn(1L, createRequest());

        assertThat(rejected.getStatus()).isEqualTo(CoachProfileStatus.PENDING);
        assertThat(rejected.getRejectionReason()).isNull();
        assertThat(rejected.getHeadline()).isEqualTo("Deneyimli koç");
        // Resubmission updates existing row — no new save() call.
        verify(coachProfileRepository, never()).save(any());
    }

    @Test
    void create_unknownUniversity_throwsNotFound() {
        when(coachProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(new User()));
        when(universityRepository.findById(5L)).thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.createOwn(1L, createRequest()));
        assertThat(ex.getErrorCode()).isEqualTo("UNIVERSITY_NOT_FOUND");
    }

    @Test
    void approve_pendingProfile_becomesApproved() {
        CoachProfile profile = new CoachProfile();
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        profile.setUser(user);
        profile.setStatus(CoachProfileStatus.PENDING);
        profile.setRejectionReason("old reason");
        when(coachProfileRepository.findById(10L)).thenReturn(Optional.of(profile));

        service.approve(10L);

        assertThat(profile.getStatus()).isEqualTo(CoachProfileStatus.APPROVED);
        assertThat(profile.getRejectionReason()).isNull();
    }

    @Test
    void approve_nonPending_throwsConflict() {
        CoachProfile profile = new CoachProfile();
        User user = new User();
        user.setStatus(UserStatus.ACTIVE);
        profile.setUser(user);
        profile.setStatus(CoachProfileStatus.APPROVED);
        when(coachProfileRepository.findById(10L)).thenReturn(Optional.of(profile));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.approve(10L));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void approve_deletedCoach_isRejected() {
        CoachProfile profile = new CoachProfile();
        User user = new User();
        user.setStatus(UserStatus.DELETED);
        profile.setUser(user);
        profile.setStatus(CoachProfileStatus.PENDING);
        when(coachProfileRepository.findById(10L)).thenReturn(Optional.of(profile));

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.approve(10L));

        assertThat(ex.getErrorCode()).isEqualTo("COACH_ACCOUNT_NOT_ACTIVE");
        assertThat(profile.getStatus()).isEqualTo(CoachProfileStatus.PENDING);
    }

    @Test
    void reject_pendingProfile_setsReasonAndStatus() {
        CoachProfile profile = new CoachProfile();
        profile.setStatus(CoachProfileStatus.PENDING);
        when(coachProfileRepository.findById(10L)).thenReturn(Optional.of(profile));

        service.reject(10L, "Eksik bilgi");

        assertThat(profile.getStatus()).isEqualTo(CoachProfileStatus.REJECTED);
        assertThat(profile.getRejectionReason()).isEqualTo("Eksik bilgi");
    }

    @Test
    void updateOwn_doesNotChangeStatus() {
        CoachProfile profile = new CoachProfile();
        profile.setStatus(CoachProfileStatus.APPROVED);
        when(coachProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(universityRepository.findById(5L)).thenReturn(Optional.of(new University()));

        service.updateOwn(1L, new CoachProfileUpdateRequest("Yeni başlık", "bio", 5L, "dep", 2021,
                Set.of(Track.VERBAL)));

        assertThat(profile.getHeadline()).isEqualTo("Yeni başlık");
        assertThat(profile.getStatus()).isEqualTo(CoachProfileStatus.APPROVED); // unchanged
    }
}
