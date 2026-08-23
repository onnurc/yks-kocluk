package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.Message;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.mapper.SessionMapper;
import com.ykskocluk.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
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

    @Test
    void conversationSummaryBatchLoadsPerConversationState() {
        User coachUser = new User(); ReflectionTestUtils.setField(coachUser, "id", 7L);
        CoachProfile coach = new CoachProfile(); ReflectionTestUtils.setField(coach, "id", 8L); coach.setUser(coachUser);
        User firstStudent = student(11L, "First Student");
        User secondStudent = student(12L, "Second Student");
        Conversation first = conversation(21L, coach, firstStudent);
        Conversation second = conversation(22L, coach, secondStudent);
        Message latest = new Message(); ReflectionTestUtils.setField(latest, "id", 31L);
        latest.setConversation(first); latest.setContent("latest message");
        ConversationUnreadCount unread = mock(ConversationUnreadCount.class);

        when(coaches.findByUserId(7L)).thenReturn(Optional.of(coach));
        when(conversations.findByCoachProfileIdOrderByLastMessageAtDesc(8L)).thenReturn(List.of(first, second));
        when(messages.findLatestByConversationIds(List.of(21L, 22L))).thenReturn(List.of(latest));
        when(messages.countUnreadByConversationIds(List.of(21L, 22L), 7L)).thenReturn(List.of(unread));
        when(unread.getConversationId()).thenReturn(21L);
        when(unread.getUnreadCount()).thenReturn(3L);
        when(subscriptions.findLiveStudentIds(8L, List.of(11L, 12L))).thenReturn(List.of(11L));

        var result = service.conversationSummaries(7L);

        assertAll(
                () -> assertEquals(2, result.size()),
                () -> assertEquals("latest message", result.get(0).lastMessagePreview()),
                () -> assertEquals(3, result.get(0).unreadCount()),
                () -> assertTrue(result.get(0).currentAccess()),
                () -> assertNull(result.get(1).lastMessagePreview()),
                () -> assertFalse(result.get(1).currentAccess()));
        verify(messages, never()).findFirstByConversationIdOrderByCreatedAtDesc(anyLong());
        verify(messages, never()).countByConversationIdAndSenderIdNotAndReadAtIsNull(anyLong(), anyLong());
        verify(subscriptions, never()).existsLiveSubscription(anyLong(), anyLong());
    }

    @Test
    void sessionsBuildsTypedPredicatesInsteadOfPassingNullFiltersToPostgres() {
        User user = new User(); ReflectionTestUtils.setField(user, "id", 7L);
        CoachProfile coach = new CoachProfile(); ReflectionTestUtils.setField(coach, "id", 8L); coach.setUser(user);
        var from = Instant.parse("2026-08-23T12:00:00Z");
        var pageable = PageRequest.of(0, 4, Sort.by("startTime").ascending());
        when(coaches.findByUserId(7L)).thenReturn(Optional.of(coach));
        when(sessions.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty(pageable));

        var result = service.sessions(7L, from, null, SessionStatus.PLANNED, null, pageable);

        assertAll(
                () -> assertTrue(result.content().isEmpty()),
                () -> assertEquals(0, result.totalElements()));
        verify(sessions).findAll(any(Specification.class), eq(pageable));
    }

    private User student(long id, String name) {
        User user = new User(); ReflectionTestUtils.setField(user, "id", id); user.setFullName(name); return user;
    }

    private Conversation conversation(long id, CoachProfile coach, User student) {
        Conversation conversation = new Conversation(); ReflectionTestUtils.setField(conversation, "id", id);
        conversation.setCoachProfile(coach); conversation.setStudent(student); conversation.setLastMessageAt(Instant.now());
        return conversation;
    }
}
