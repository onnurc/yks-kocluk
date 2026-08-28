package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.AvailabilityCreateRequest;
import com.ykskocluk.demo.entity.CoachAvailability;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.exception.ApiException;
import org.springframework.http.HttpStatus;
import com.ykskocluk.demo.mapper.AvailabilityMapper;
import com.ykskocluk.demo.repository.CoachAvailabilityRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachAvailabilityServiceTest {

    @Mock CoachAvailabilityRepository availabilityRepository;
    @Mock CoachProfileRepository coachProfileRepository;
    @Mock AvailabilityMapper availabilityMapper;

    CoachAvailabilityService service;

    private static final long COACH_USER_ID = 5L;
    private static final long PROFILE_ID = 9L;

    @BeforeEach
    void setUp() {
        service = new CoachAvailabilityService(availabilityRepository, coachProfileRepository, availabilityMapper);

        CoachProfile profile = new CoachProfile();
        ReflectionTestUtils.setField(profile, "id", PROFILE_ID);
        profile.setStatus(CoachProfileStatus.APPROVED);
        lenient().when(coachProfileRepository.findByUserId(COACH_USER_ID)).thenReturn(Optional.of(profile));
    }

    private AvailabilityCreateRequest request(Instant start, Instant end) {
        return new AvailabilityCreateRequest(start, end);
    }

    @Test
    void create_success_persistsUnbookedSlot() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        service.createOwn(COACH_USER_ID, request(start, start.plus(1, ChronoUnit.HOURS)));
        verify(availabilityRepository).saveAndFlush(any(CoachAvailability.class));
    }

    @Test
    void create_pastSlot_throwsBadRequest() {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, start.plus(1, ChronoUnit.HOURS))));
        assertThat(ex.getErrorCode()).isEqualTo("SLOT_IN_PAST");
        verify(availabilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_endNotAfterStart_throwsBadRequest() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, start))); // end == start
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_SLOT_RANGE");
        verify(availabilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_durationBelowFifteenMinutes_throwsBadRequest() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, start.plus(14, ChronoUnit.MINUTES))));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_SLOT_DURATION");
        verify(availabilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_durationAboveEightHours_throwsBadRequest() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, start.plus(9, ChronoUnit.HOURS))));
        assertThat(ex.getErrorCode()).isEqualTo("INVALID_SLOT_DURATION");
        verify(availabilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_moreThanOneYearAhead_throwsBadRequest() {
        Instant start = Instant.now().plus(367, ChronoUnit.DAYS);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, start.plus(1, ChronoUnit.HOURS))));
        assertThat(ex.getErrorCode()).isEqualTo("SLOT_TOO_FAR_IN_FUTURE");
        verify(availabilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_duplicateStart_throwsConflict() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        when(availabilityRepository.saveAndFlush(any(CoachAvailability.class)))
                .thenThrow(new DataIntegrityViolationException("uq_availability_coach_start"));
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, start.plus(1, ChronoUnit.HOURS))));
        assertThat(ex.getErrorCode()).isEqualTo("SLOT_DUPLICATE");
    }

    @Test
    void create_noProfile_throwsNotFound() {
        when(coachProfileRepository.findByUserId(COACH_USER_ID)).thenReturn(Optional.empty());
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, start.plus(1, ChronoUnit.HOURS))));
        assertThat(ex.getErrorCode()).isEqualTo("PROFILE_NOT_FOUND");
    }

    @Test
    void delete_bookedSlot_throwsConflict() {
        CoachAvailability booked = new CoachAvailability();
        booked.setBooked(true);
        when(availabilityRepository.findByIdAndCoachProfileId(1L, PROFILE_ID)).thenReturn(Optional.of(booked));
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.deleteOwn(COACH_USER_ID, 1L));
        assertThat(ex.getErrorCode()).isEqualTo("SLOT_BOOKED");
        verify(availabilityRepository, never()).delete(any());
    }

    @Test
    void delete_unbookedSlot_deletes() {
        CoachAvailability slot = new CoachAvailability();
        slot.setBooked(false);
        when(availabilityRepository.findByIdAndCoachProfileId(1L, PROFILE_ID)).thenReturn(Optional.of(slot));
        service.deleteOwn(COACH_USER_ID, 1L);
        verify(availabilityRepository).delete(slot);
    }

    @Test
    void delete_notFound_throwsNotFound() {
        when(availabilityRepository.findByIdAndCoachProfileId(1L, PROFILE_ID)).thenReturn(Optional.empty());
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.deleteOwn(COACH_USER_ID, 1L));
        assertThat(ex.getErrorCode()).isEqualTo("SLOT_NOT_FOUND");
    }

    @Test
    void create_coachPending_throwsForbidden() {
        CoachProfile pendingProfile = new CoachProfile();
        pendingProfile.setStatus(CoachProfileStatus.PENDING);
        when(coachProfileRepository.findByUserId(COACH_USER_ID)).thenReturn(Optional.of(pendingProfile));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, start.plus(1, ChronoUnit.HOURS))));

        assertThat(ex.getErrorCode()).isEqualTo("COACH_NOT_APPROVED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(availabilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_coachRejected_throwsForbidden() {
        CoachProfile rejectedProfile = new CoachProfile();
        rejectedProfile.setStatus(CoachProfileStatus.REJECTED);
        when(coachProfileRepository.findByUserId(COACH_USER_ID)).thenReturn(Optional.of(rejectedProfile));

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, start.plus(1, ChronoUnit.HOURS))));

        assertThat(ex.getErrorCode()).isEqualTo("COACH_NOT_APPROVED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(availabilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_overlappingSlot_throwsConflict() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        when(availabilityRepository.existsOverlapping(PROFILE_ID, start, end)).thenReturn(true);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.createOwn(COACH_USER_ID, request(start, end)));

        assertThat(ex.getErrorCode()).isEqualTo("SLOT_OVERLAP");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(availabilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_nonOverlappingSlot_succeeds() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        when(availabilityRepository.existsOverlapping(PROFILE_ID, start, end)).thenReturn(false);

        service.createOwn(COACH_USER_ID, request(start, end));

        verify(availabilityRepository).saveAndFlush(any(CoachAvailability.class));
    }
}
