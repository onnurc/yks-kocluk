package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.SessionCreateRequest;
import com.ykskocluk.demo.dto.SessionResponse;
import com.ykskocluk.demo.entity.CoachAvailability;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Package;
import com.ykskocluk.demo.entity.Session;
import com.ykskocluk.demo.entity.Subscription;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.SessionStatus;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.SessionMapper;
import com.ykskocluk.demo.repository.CoachAvailabilityRepository;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.SessionRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock SessionRepository sessionRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock CoachAvailabilityRepository availabilityRepository;
    @Mock UserRepository userRepository;
    @Mock CoachProfileRepository coachProfileRepository;
    @Mock SessionMapper sessionMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock com.ykskocluk.demo.service.ConsentService consentService;

    SessionService service;

    private static final long STUDENT_ID = 1L;
    private static final long COACH_ID = 7L;
    private static final long SLOT_ID = 3L;
    private static final long SUB_ID = 11L;

    private CoachAvailability slot;

    @BeforeEach
    void setUp() {
        service = new SessionService(sessionRepository, subscriptionRepository, availabilityRepository,
                userRepository, coachProfileRepository, sessionMapper, eventPublisher, consentService);

        CoachProfile coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_ID);
        coach.setStatus(CoachProfileStatus.APPROVED);
        User coachUser = new User();
        coachUser.setFullName("Coach");
        coach.setUser(coachUser);

        slot = new CoachAvailability();
        ReflectionTestUtils.setField(slot, "id", SLOT_ID);
        slot.setCoachProfile(coach);
        slot.setStartTime(Instant.now().plus(2, ChronoUnit.DAYS));
        slot.setEndTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS));
        slot.setBooked(false);

        Package pkg = new Package();
        pkg.setWeeklySessions(1);

        Subscription sub = new Subscription();
        ReflectionTestUtils.setField(sub, "id", SUB_ID);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setPkg(pkg);

        lenient().when(availabilityRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot));
        lenient().when(subscriptionRepository.findLiveSubscription(
                STUDENT_ID, COACH_ID)).thenReturn(Optional.of(sub));
        lenient().when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(new User()));
        lenient().when(sessionRepository.countQuotaConsuming(eq(SUB_ID), any(), any(), any())).thenReturn(0L);
        lenient().when(sessionMapper.toResponse(any())).thenReturn(
                new SessionResponse(1L, COACH_ID, "Coach", "Student", SLOT_ID,
                        SessionStatus.PLANNED, slot.getStartTime(), slot.getEndTime(), null));
    }

    private SessionCreateRequest request() {
        return new SessionCreateRequest(SLOT_ID);
    }

    @Test
    void book_success_createsPlanned_snapshotsTime_flipsBooked() {
        service.book(STUDENT_ID, request());

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).saveAndFlush(captor.capture());
        Session saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.PLANNED);
        assertThat(saved.getStartTime()).isEqualTo(slot.getStartTime());
        assertThat(saved.getAvailability()).isSameAs(slot);
        assertThat(slot.isBooked()).isTrue();
    }

    @Test
    void book_slotNotFound_throwsNotFound() {
        when(availabilityRepository.findById(SLOT_ID)).thenReturn(Optional.empty());
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.book(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("SLOT_NOT_FOUND");
    }

    @Test
    void book_pastSlot_throwsBadRequest() {
        slot.setStartTime(Instant.now().minus(1, ChronoUnit.HOURS));
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.book(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("SLOT_IN_PAST");
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void book_noActiveSubscription_throwsConflict() {
        when(subscriptionRepository.findLiveSubscription(
                STUDENT_ID, COACH_ID)).thenReturn(Optional.empty());
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.book(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("NO_ACTIVE_SUBSCRIPTION");
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void book_quotaExceeded_throwsConflict_andDoesNotSave() {
        when(sessionRepository.countQuotaConsuming(eq(SUB_ID), any(), any(), any())).thenReturn(1L); // limit is 1
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.book(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("QUOTA_EXCEEDED");
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void book_slotTakenAtInsert_throwsConflict() {
        when(sessionRepository.saveAndFlush(any(Session.class)))
                .thenThrow(new DataIntegrityViolationException("uq_sessions_availability"));
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.book(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("SLOT_TAKEN");
    }

    @Test
    void book_coachListing_requiresProfile() {
        when(coachProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.myCoachSessions(99L));
        assertThat(ex.getErrorCode()).isEqualTo("PROFILE_NOT_FOUND");
    }

    @Test
    void book_coachPending_throwsForbidden() {
        slot.getCoachProfile().setStatus(CoachProfileStatus.PENDING);
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.book(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("COACH_NOT_APPROVED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(sessionRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void book_coachRejected_throwsForbidden() {
        slot.getCoachProfile().setStatus(CoachProfileStatus.REJECTED);
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.book(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("COACH_NOT_APPROVED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(sessionRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void book_minorStudentWithoutConsent_throwsForbidden() {
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "MINOR_CONSENT_REQUIRED", "18 yaş altı öğrenciler için veli onayı gereklidir"))
                .when(consentService).checkConsentRequiredForAction(any());

        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.book(STUDENT_ID, request()));
        assertThat(ex.getErrorCode()).isEqualTo("MINOR_CONSENT_REQUIRED");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(sessionRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void book_minorStudentWithConsent_succeeds() {
        // consentService.checkConsentRequiredForAction does not throw exception (mock default behavior)
        service.book(STUDENT_ID, request());

        verify(sessionRepository).saveAndFlush(any(Session.class));
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void book_consentGateOrdering() {
        service.book(STUDENT_ID, request());

        InOrder inOrder = inOrder(consentService, sessionRepository, eventPublisher);
        inOrder.verify(consentService).checkConsentRequiredForAction(any());
        inOrder.verify(sessionRepository).saveAndFlush(any(Session.class));
        inOrder.verify(eventPublisher).publishEvent(any(Object.class));
    }
}
