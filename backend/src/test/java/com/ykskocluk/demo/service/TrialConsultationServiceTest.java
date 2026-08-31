package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.TrialConsultationCreateRequest;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrialConsultationServiceTest {
    @Mock TrialConsultationRepository trials;
    @Mock CoachAvailabilityRepository availabilities;
    @Mock CoachProfileRepository coaches;
    @Mock SessionRepository sessions;
    @Mock UserRepository users;
    @Mock SubscriptionRepository subscriptions;
    @Mock AccountReadinessService readiness;
    TrialConsultationService service;

    @BeforeEach void setUp() {
        service = new TrialConsultationService(trials, availabilities, coaches, sessions, users, subscriptions, readiness,
                Clock.systemUTC());
    }

    @Test
    void nonSubscribedReadyStudentCanRequest_withoutCreatingPaidArtifacts() {
        User student = user(10L, Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = coach(20L, 30L, CoachProfileStatus.APPROVED);
        CoachAvailability slot = slot(40L, coach, Instant.now().plusSeconds(7200));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(student));
        when(availabilities.findByIdForUpdate(40L)).thenReturn(Optional.of(slot));
        when(trials.existsByStudentIdAndCoachProfileIdAndStatusIn(eq(10L), eq(20L), any())).thenReturn(false);
        when(trials.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.request(10L, new TrialConsultationCreateRequest(40L));

        assertEquals(TrialConsultationStatus.REQUESTED, response.status());
        assertTrue(slot.isBooked());
        verify(readiness).requireReady(student);
        verify(trials).saveAndFlush(any(TrialConsultation.class));
    }

    @Test
    void activePaidSubscriptionBlocksTrialWithCurrentOrDifferentCoachBeforeSlotLookup() {
        User student = user(18L, Role.STUDENT, UserStatus.ACTIVE);
        when(users.findByIdForUpdate(18L)).thenReturn(Optional.of(student));
        when(subscriptions.existsByStudentIdAndStatusIn(eq(18L), any())).thenReturn(true);

        ApiException error = assertThrows(ApiException.class,
                () -> service.request(18L, new TrialConsultationCreateRequest(99L)));

        assertEquals("ACTIVE_COACH_EXISTS", error.getErrorCode());
        verifyNoInteractions(availabilities);
    }

    @Test
    void overlapIsRejectedDeterministically() {
        User student = user(11L, Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = coach(21L, 31L, CoachProfileStatus.APPROVED);
        CoachAvailability slot = slot(41L, coach, Instant.now().plusSeconds(7200));
        slot.setBooked(true);
        when(users.findByIdForUpdate(11L)).thenReturn(Optional.of(student));
        when(availabilities.findByIdForUpdate(41L)).thenReturn(Optional.of(slot));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(11L, new TrialConsultationCreateRequest(41L)));
        assertEquals("SLOT_TAKEN", ex.getErrorCode());
        verify(trials, never()).saveAndFlush(any());
    }

    @Test
    void cancellationReleasesAvailabilityAndAllowsFutureTrialRule() {
        User student = user(12L, Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = coach(22L, 32L, CoachProfileStatus.APPROVED);
        CoachAvailability slot = slot(42L, coach, Instant.now().plusSeconds(7200));
        slot.setBooked(true);
        TrialConsultation trial = new TrialConsultation();
        trial.setStudent(student); trial.setCoachProfile(coach); trial.setAvailability(slot);
        trial.setStartTime(slot.getStartTime()); trial.setEndTime(slot.getEndTime());
        trial.setStatus(TrialConsultationStatus.REQUESTED);
        when(trials.findById(50L)).thenReturn(Optional.of(trial));

        var response = service.cancelByStudent(12L, 50L);

        assertEquals(TrialConsultationStatus.CANCELLED, response.status());
        assertFalse(slot.isBooked());
        assertNull(trial.getAvailability());
    }

    @Test
    void unapprovedCoachIsRejected() {
        User student = user(13L, Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = coach(23L, 33L, CoachProfileStatus.PENDING);
        CoachAvailability slot = slot(43L, coach, Instant.now().plusSeconds(7200));
        when(users.findByIdForUpdate(13L)).thenReturn(Optional.of(student));
        when(availabilities.findByIdForUpdate(43L)).thenReturn(Optional.of(slot));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(13L, new TrialConsultationCreateRequest(43L)));
        assertEquals("COACH_NOT_AVAILABLE", ex.getErrorCode());
    }

    @Test
    void paidSessionOverlapIsRejectedEvenWhenConvenienceFlagIsFalse() {
        User student = user(14L, Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = coach(24L, 34L, CoachProfileStatus.APPROVED);
        CoachAvailability slot = slot(44L, coach, Instant.now().plusSeconds(7200));
        when(users.findByIdForUpdate(14L)).thenReturn(Optional.of(student));
        when(availabilities.findByIdForUpdate(44L)).thenReturn(Optional.of(slot));
        when(sessions.existsOverlap(eq(24L), any(), eq(slot.getStartTime()), eq(slot.getEndTime()))).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(14L, new TrialConsultationCreateRequest(44L)));
        assertEquals("SLOT_TAKEN", ex.getErrorCode());
    }

    @Test
    void confirmedStartedTrialCanCompleteOrBecomeNoShow() {
        CoachProfile coach = coach(25L, 35L, CoachProfileStatus.APPROVED);
        when(coaches.findByUserId(35L)).thenReturn(Optional.of(coach));
        TrialConsultation completed = trial(60L, coach, TrialConsultationStatus.CONFIRMED);
        TrialConsultation noShow = trial(61L, coach, TrialConsultationStatus.CONFIRMED);
        when(trials.findByIdAndCoachProfileId(60L, 25L)).thenReturn(Optional.of(completed));
        when(trials.findByIdAndCoachProfileId(61L, 25L)).thenReturn(Optional.of(noShow));

        assertEquals(TrialConsultationStatus.COMPLETED, service.complete(35L, 60L).status());
        assertEquals(TrialConsultationStatus.NO_SHOW, service.noShow(35L, 61L).status());
    }

    @Test
    void thirdTrialInsideAnyRollingSevenDaysIsRejected() {
        Instant candidate = Instant.parse("2026-09-05T10:00:00Z");
        service = new TrialConsultationService(trials, availabilities, coaches, sessions, users, subscriptions, readiness,
                Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC));
        User student = user(15L, Role.STUDENT, UserStatus.ACTIVE);
        CoachProfile coach = coach(26L, 36L, CoachProfileStatus.APPROVED);
        CoachAvailability slot = slot(45L, coach, candidate);
        when(users.findByIdForUpdate(15L)).thenReturn(Optional.of(student));
        when(availabilities.findByIdForUpdate(45L)).thenReturn(Optional.of(slot));
        when(trials.findByStudentIdAndStatusInAndStartTimeGreaterThanEqualAndStartTimeLessThanEqual(
                eq(15L), any(), any(), any())).thenReturn(List.of(
                scheduledTrial(coach, student, candidate.minusSeconds(2 * 86400L)),
                scheduledTrial(coach(27L, 37L, CoachProfileStatus.APPROVED), student, candidate.plusSeconds(86400L))));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.request(15L, new TrialConsultationCreateRequest(45L)));

        assertEquals("TRIAL_WEEKLY_LIMIT_REACHED", ex.getErrorCode());
        verify(trials, never()).saveAndFlush(any());
    }

    @Test
    void adminConfirmationStoresMeetingLinkAndIsIdempotent() {
        Instant now = Instant.parse("2026-09-01T10:00:00Z");
        service = new TrialConsultationService(trials, availabilities, coaches, sessions, users, subscriptions, readiness,
                Clock.fixed(now, ZoneOffset.UTC));
        User admin = user(1L, Role.ADMIN, UserStatus.ACTIVE);
        CoachProfile coach = coach(28L, 38L, CoachProfileStatus.APPROVED);
        TrialConsultation trial = scheduledTrial(coach, user(16L, Role.STUDENT, UserStatus.ACTIVE), now.plusSeconds(86400));
        when(users.findById(1L)).thenReturn(Optional.of(admin)); when(trials.findById(70L)).thenReturn(Optional.of(trial));

        var first = service.confirmByAdmin(1L, 70L, "https://meet.google.com/abc-defg-hij");
        var repeated = service.confirmByAdmin(1L, 70L, "https://meet.google.com/abc-defg-hij");

        assertTrue(first.newlyConfirmed()); assertFalse(repeated.newlyConfirmed());
        assertEquals(TrialConsultationStatus.CONFIRMED, trial.getStatus());
        assertEquals("https://meet.google.com/abc-defg-hij", trial.getMeetingUrl());
        assertEquals(admin, trial.getConfirmedBy());
    }

    private User user(Long id, Role role, UserStatus status) {
        User user = new User(); ReflectionTestUtils.setField(user, "id", id);
        user.setFullName("User " + id); user.setRole(role); user.setStatus(status); return user;
    }
    private CoachProfile coach(Long id, Long userId, CoachProfileStatus status) {
        CoachProfile coach = new CoachProfile(); ReflectionTestUtils.setField(coach, "id", id);
        coach.setUser(user(userId, Role.COACH, UserStatus.ACTIVE)); coach.setStatus(status); return coach;
    }
    private CoachAvailability slot(Long id, CoachProfile coach, Instant start) {
        CoachAvailability slot = new CoachAvailability(); ReflectionTestUtils.setField(slot, "id", id);
        slot.setCoachProfile(coach); slot.setStartTime(start); slot.setEndTime(start.plusSeconds(1800));
        slot.setPurpose(AvailabilityPurpose.TRIAL); return slot;
    }
    private TrialConsultation trial(Long id, CoachProfile coach, TrialConsultationStatus status) {
        TrialConsultation trial = new TrialConsultation(); ReflectionTestUtils.setField(trial, "id", id);
        trial.setCoachProfile(coach); trial.setStudent(user(id + 100, Role.STUDENT, UserStatus.ACTIVE));
        trial.setStartTime(Instant.now().minusSeconds(7200)); trial.setEndTime(Instant.now().minusSeconds(3600));
        trial.setStatus(status); return trial;
    }
    private TrialConsultation scheduledTrial(CoachProfile coach, User student, Instant start) {
        TrialConsultation trial = new TrialConsultation(); trial.setCoachProfile(coach); trial.setStudent(student);
        trial.setStartTime(start); trial.setEndTime(start.plusSeconds(1800)); trial.setStatus(TrialConsultationStatus.REQUESTED);
        return trial;
    }
}
