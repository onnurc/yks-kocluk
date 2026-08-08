package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.mapper.SessionMapper;
import com.ykskocluk.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachDashboardServiceTest {
    @Mock CoachProfileRepository coaches;
    @Mock SubscriptionRepository subscriptions;
    @Mock SessionRepository sessions;
    @Mock CoachAvailabilityRepository availabilities;
    @Mock MessageRepository messages;
    @Mock ConversationRepository conversations;
    @Mock TrialConsultationRepository trials;
    @Mock PaymentRepository payments;
    @Mock PackageRepository packages;
    @Mock SessionMapper sessionMapper;
    CoachDashboardService service;

    @BeforeEach void setUp() {
        service = new CoachDashboardService(coaches, subscriptions, sessions, availabilities, messages,
                conversations, trials, payments, packages, sessionMapper);
    }

    @Test
    void summaryUsesDistinctLiveStudentsMonthlySessionsAndUnreadState() {
        User user = new User(); ReflectionTestUtils.setField(user, "id", 7L);
        CoachProfile coach = new CoachProfile(); ReflectionTestUtils.setField(coach, "id", 8L); coach.setUser(user);
        when(coaches.findByUserId(7L)).thenReturn(Optional.of(coach));
        when(subscriptions.countDistinctStudents(eq(8L), any())).thenReturn(3L);
        when(sessions.countByCoachProfileIdAndStatusAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                eq(8L), eq(SessionStatus.COMPLETED), any(Instant.class), any(Instant.class))).thenReturn(4L);
        when(sessions.countByCoachProfileIdAndStatusAndStartTimeAfter(eq(8L), eq(SessionStatus.PLANNED), any()))
                .thenReturn(2L);
        when(messages.countUnreadForCoach(7L)).thenReturn(6L);
        when(availabilities.existsByCoachProfileIdAndBookedFalseAndStartTimeAfter(eq(8L), any())).thenReturn(true);
        when(trials.countByCoachAndStatus(8L, TrialConsultationStatus.REQUESTED)).thenReturn(1L);

        var summary = service.summary(7L);

        assertAll(
                () -> assertEquals(3, summary.activeStudentCount()),
                () -> assertEquals(4, summary.completedSessionsThisMonth()),
                () -> assertEquals(2, summary.upcomingSessionCount()),
                () -> assertEquals(6, summary.unreadMessageCount()),
                () -> assertTrue(summary.availabilityConfigured()),
                () -> assertEquals(1, summary.pendingTrialConsultationCount()));
    }
}
