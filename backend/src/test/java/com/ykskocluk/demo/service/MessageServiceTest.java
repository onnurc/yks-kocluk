package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.ConversationResponse;
import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.Conversation;
import com.ykskocluk.demo.entity.Message;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.SubscriptionStatus;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.mapper.ConversationMapper;
import com.ykskocluk.demo.mapper.MessageMapper;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.ConversationRepository;
import com.ykskocluk.demo.repository.MessageRepository;
import com.ykskocluk.demo.repository.SubscriptionRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock MessageRepository messageRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock CoachProfileRepository coachProfileRepository;
    @Mock UserRepository userRepository;
    @Mock ConversationMapper conversationMapper;
    @Mock MessageMapper messageMapper;
    @Mock AccountReadinessService accountReadinessService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ChatPresenceService presenceService;

    MessageService service;

    private static final long STUDENT_ID = 1L;
    private static final long COACH_USER_ID = 2L;
    private static final long COACH_PROFILE_ID = 7L;
    private static final long CONVERSATION_ID = 50L;
    private static final long STRANGER_ID = 99L;

    private CoachProfile coach;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        service = new MessageService(conversationRepository, messageRepository, subscriptionRepository,
                coachProfileRepository, userRepository, conversationMapper, messageMapper, accountReadinessService,
                eventPublisher, presenceService);

        User coachUser = new User();
        ReflectionTestUtils.setField(coachUser, "id", COACH_USER_ID);
        coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_PROFILE_ID);
        coach.setUser(coachUser);

        User studentUser = new User();
        ReflectionTestUtils.setField(studentUser, "id", STUDENT_ID);
        lenient().when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(studentUser));
        User stranger = new User();
        ReflectionTestUtils.setField(stranger, "id", STRANGER_ID);
        lenient().when(userRepository.findById(STRANGER_ID)).thenReturn(Optional.of(stranger));

        conversation = new Conversation();
        ReflectionTestUtils.setField(conversation, "id", CONVERSATION_ID);
        conversation.setStudent(studentUser);
        conversation.setCoachProfile(coach);
        conversation.setLastMessageAt(Instant.now().minusSeconds(3600));

        lenient().when(conversationMapper.toResponse(any(), org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.nullable(String.class),
                        org.mockito.ArgumentMatchers.nullable(Instant.class),
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new ConversationResponse(CONVERSATION_ID, COACH_PROFILE_ID, "Coach", "Student",
                        null, null, 0, COACH_USER_ID, false,
                        com.ykskocluk.demo.dto.ConversationObserverResponse.platformAdmin()));
    }

    // --- gate (checkpoints 1 & 2) ---

    @Test
    void openConversation_noSubscriptionEver_throws403() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.openConversation(STUDENT_ID, COACH_PROFILE_ID));
        assertThat(ex.getErrorCode()).isEqualTo("MESSAGING_NOT_ALLOWED");
        assertThat(ex.getStatus().value()).isEqualTo(403);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void openConversation_pendingPaymentSubscription_throws403() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.openConversation(STUDENT_ID, COACH_PROFILE_ID));
        assertThat(ex.getErrorCode()).isEqualTo("MESSAGING_NOT_ALLOWED");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void openConversation_expiredSubscription_allowed_createsConversation() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(true);
        when(conversationRepository.findByStudentIdAndCoachProfileId(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(Optional.empty());
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.openConversation(STUDENT_ID, COACH_PROFILE_ID);

        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void openConversation_terminatedSubscription_throws403() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.openConversation(STUDENT_ID, COACH_PROFILE_ID));
        assertThat(ex.getErrorCode()).isEqualTo("MESSAGING_NOT_ALLOWED");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void openConversation_minorWithoutLegacyConsent_allowedWhenCommonOnboardingComplete() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(true);
        when(conversationRepository.findByStudentIdAndCoachProfileId(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(Optional.empty());
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        student.setDateOfBirth(java.time.LocalDate.now().minusYears(16));
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.openConversation(STUDENT_ID, COACH_PROFILE_ID);

        verify(conversationRepository).save(any(Conversation.class));
        verify(accountReadinessService).requireReady(student);
    }

    @Test
    void openConversation_pastDueSubscription_allowed_createsConversation() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(true);
        when(conversationRepository.findByStudentIdAndCoachProfileId(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(Optional.empty());
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.openConversation(STUDENT_ID, COACH_PROFILE_ID);

        verify(conversationRepository).save(any(Conversation.class));
    }

    // --- membership re-check on send AND read (checkpoint 3) ---

    @Test
    void sendMessage_nonParticipant_throws403() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.sendMessage(STRANGER_ID, CONVERSATION_ID, "hi"));
        assertThat(ex.getErrorCode()).isEqualTo("NOT_CONVERSATION_PARTICIPANT");
        assertThat(ex.getStatus().value()).isEqualTo(403);
        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void markRead_nonParticipant_throws403() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.markRead(STRANGER_ID, CONVERSATION_ID));
        assertThat(ex.getErrorCode()).isEqualTo("NOT_CONVERSATION_PARTICIPANT");
        verify(messageRepository, never()).markRead(any(), any(), any());
    }

    // --- last_message_at bumped in same tx as insert (checkpoint 4) ---

    @Test
    void sendMessage_participant_persists_andBumpsLastMessageAt() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(STUDENT_ID, COACH_PROFILE_ID, SubscriptionStatus.ACTIVE)).thenReturn(true);
        User student = conversation.getStudent();
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        Instant sentAt = Instant.now();
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "createdAt", sentAt);
            return m;
        });
        when(messageMapper.toResponse(any())).thenReturn(
                new MessageResponse(1L, CONVERSATION_ID, STUDENT_ID, "Student", "hi", sentAt, null));

        service.sendMessage(STUDENT_ID, CONVERSATION_ID, "hi");

        verify(messageRepository).saveAndFlush(any(Message.class));
        assertThat(conversation.getLastMessageAt()).isEqualTo(sentAt); // bumped to the message's sent time
    }

    @Test
    void sendMessage_studentSubscriptionLaterInactive_throws403() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(STUDENT_ID, COACH_PROFILE_ID, SubscriptionStatus.ACTIVE)).thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.sendMessage(STUDENT_ID, CONVERSATION_ID, "hi"));
        assertThat(ex.getErrorCode()).isEqualTo("MESSAGING_NOT_ALLOWED");
        verify(messageRepository, never()).saveAndFlush(any());
    }

    @Test
    void sendMessage_coachAlwaysAllowed_evenIfStudentInactive() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(userRepository.findById(COACH_USER_ID)).thenReturn(Optional.of(coach.getUser()));
        Instant sentAt = Instant.now();
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "createdAt", sentAt);
            return m;
        });
        when(messageMapper.toResponse(any())).thenReturn(
                new MessageResponse(1L, CONVERSATION_ID, COACH_USER_ID, "Coach", "hi student", sentAt, null));

        service.sendMessage(COACH_USER_ID, CONVERSATION_ID, "hi student");

        verify(messageRepository).saveAndFlush(any(Message.class));
    }

    // --- mark-read delegates with the reader id (the "only other party" clause lives in the query) ---

    @Test
    void markRead_participant_callsRepoWithReaderId() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.markRead(eq(CONVERSATION_ID), eq(COACH_USER_ID), any())).thenReturn(2);

        int flipped = service.markRead(COACH_USER_ID, CONVERSATION_ID);

        assertThat(flipped).isEqualTo(2);
        verify(messageRepository).markRead(eq(CONVERSATION_ID), eq(COACH_USER_ID), any());
    }

    // --- Phase 5 list and read tests ---

    @Test
    void myConversations_inactiveStudent_returnsEmptyList() {
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        student.setRole(com.ykskocluk.demo.enums.Role.STUDENT);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(conversationRepository.findForUser(STUDENT_ID)).thenReturn(java.util.List.of(conversation));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(false);

        java.util.List<ConversationResponse> res = service.myConversations(STUDENT_ID);
        assertThat(res).isEmpty();
    }

    @Test
    void myConversations_studentWithOneActiveAndOneExpired_returnsBoth() {
        User student = new User();
        ReflectionTestUtils.setField(student, "id", STUDENT_ID);
        student.setRole(com.ykskocluk.demo.enums.Role.STUDENT);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

        Conversation activeConv = conversation; // coach profile id COACH_PROFILE_ID

        Conversation expiredConv = new Conversation();
        ReflectionTestUtils.setField(expiredConv, "id", 101L);
        expiredConv.setStudent(student);
        CoachProfile coachB = new CoachProfile();
        ReflectionTestUtils.setField(coachB, "id", 202L);
        User coachUserB = new User();
        ReflectionTestUtils.setField(coachUserB, "id", 302L);
        coachUserB.setFullName("Coach B");
        coachB.setUser(coachUserB);
        expiredConv.setCoachProfile(coachB);

        when(conversationRepository.findForUser(STUDENT_ID)).thenReturn(java.util.List.of(activeConv, expiredConv));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(true);
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, 202L)).thenReturn(true);

        java.util.List<ConversationResponse> res = service.myConversations(STUDENT_ID);
        assertThat(res).hasSize(2);
    }

    @Test
    void myConversations_activeStudent_allowed() {
        User student = new User();
        student.setRole(com.ykskocluk.demo.enums.Role.STUDENT);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(conversationRepository.findForUser(STUDENT_ID)).thenReturn(java.util.List.of(conversation));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(true);

        java.util.List<ConversationResponse> res = service.myConversations(STUDENT_ID);
        assertThat(res).isNotEmpty();
    }

    @Test
    void myConversations_usesOneBulkLatestMessageQueryForPreviewAndPresence() {
        User student = conversation.getStudent();
        student.setRole(com.ykskocluk.demo.enums.Role.STUDENT);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(conversationRepository.findForUser(STUDENT_ID)).thenReturn(java.util.List.of(conversation));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(true);
        Instant sentAt = Instant.parse("2026-08-17T10:15:00Z");
        Message latest = new Message();
        latest.setConversation(conversation);
        latest.setContent("Gerçek son mesaj");
        ReflectionTestUtils.setField(latest, "createdAt", sentAt);
        when(messageRepository.findLatestByConversationIds(java.util.List.of(CONVERSATION_ID)))
                .thenReturn(java.util.List.of(latest));
        when(presenceService.isOnline(COACH_USER_ID)).thenReturn(true);

        service.myConversations(STUDENT_ID);

        verify(messageRepository).findLatestByConversationIds(java.util.List.of(CONVERSATION_ID));
        verify(conversationMapper).toResponse(conversation, 0L, "Gerçek son mesaj", sentAt,
                COACH_USER_ID, true);
        verify(messageRepository, never()).findFirstByConversationIdOrderByCreatedAtDesc(any());
    }

    @Test
    void myConversations_pastDueStudent_allowed() {
        User student = new User();
        student.setRole(com.ykskocluk.demo.enums.Role.STUDENT);
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(conversationRepository.findForUser(STUDENT_ID)).thenReturn(java.util.List.of(conversation));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(true);

        java.util.List<ConversationResponse> res = service.myConversations(STUDENT_ID);
        assertThat(res).isNotEmpty();
    }

    @Test
    void myConversations_coachParticipant_alwaysAllowed() {
        User coachUser = new User();
        coachUser.setRole(com.ykskocluk.demo.enums.Role.COACH);
        when(userRepository.findById(COACH_USER_ID)).thenReturn(Optional.of(coachUser));
        when(conversationRepository.findForUser(COACH_USER_ID)).thenReturn(java.util.List.of(conversation));

        java.util.List<ConversationResponse> res = service.myConversations(COACH_USER_ID);
        assertThat(res).isNotEmpty();
        verify(subscriptionRepository, never()).existsHistoryAccessSubscription(any(), any());
    }

    @Test
    void history_inactiveStudent_throws403() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.history(STUDENT_ID, CONVERSATION_ID, org.springframework.data.domain.Pageable.unpaged()));
        assertThat(ex.getErrorCode()).isEqualTo("MESSAGING_NOT_ALLOWED");
    }

    @Test
    void history_activeStudent_allowed() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(true);
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(CONVERSATION_ID), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.history(STUDENT_ID, CONVERSATION_ID, org.springframework.data.domain.Pageable.unpaged());
        verify(messageRepository).findByConversationIdOrderByCreatedAtDesc(eq(CONVERSATION_ID), any());
    }

    @Test
    void history_coachParticipant_alwaysAllowed() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(CONVERSATION_ID), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.history(COACH_USER_ID, CONVERSATION_ID, org.springframework.data.domain.Pageable.unpaged());
        verify(subscriptionRepository, never()).existsHistoryAccessSubscription(any(), any());
    }

    // --- new access pass tests ---

    @Test
    void historyGate_allowsActivePastDueExpiredCancelled() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(true);
        when(conversationRepository.findByStudentIdAndCoachProfileId(STUDENT_ID, COACH_PROFILE_ID))
                .thenReturn(Optional.of(conversation));

        ConversationResponse response = service.openConversation(STUDENT_ID, COACH_PROFILE_ID);
        assertThat(response).isNotNull();
    }

    @Test
    void historyGate_rejectsPendingPaymentTerminatedOrNone() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.openConversation(STUDENT_ID, COACH_PROFILE_ID));
        assertThat(ex.getErrorCode()).isEqualTo("MESSAGING_NOT_ALLOWED");
    }

    @Test
    void sendGate_allowsActive() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(STUDENT_ID, COACH_PROFILE_ID, SubscriptionStatus.ACTIVE))
                .thenReturn(true);
        User student = conversation.getStudent();
        when(userRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        Instant sentAt = Instant.now();
        Message msg = new Message();
        ReflectionTestUtils.setField(msg, "createdAt", sentAt);
        when(messageRepository.saveAndFlush(any(Message.class))).thenReturn(msg);
        when(messageMapper.toResponse(any())).thenReturn(
                new MessageResponse(1L, CONVERSATION_ID, STUDENT_ID, "Student", "content", sentAt, null));

        MessageResponse response = service.sendMessage(STUDENT_ID, CONVERSATION_ID, "content");
        assertThat(response).isNotNull();
    }

    @Test
    void sendGate_rejectsPastDueExpiredCancelledPendingPaymentTerminatedOrNone() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(STUDENT_ID, COACH_PROFILE_ID, SubscriptionStatus.ACTIVE))
                .thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.sendMessage(STUDENT_ID, CONVERSATION_ID, "content"));
        assertThat(ex.getErrorCode()).isEqualTo("MESSAGING_NOT_ALLOWED");
    }

    @Test
    void mixedSubscriptions_allowsHistoryButRejectsSend() {
        // Mock history gate to return true (older valid subscription exists)
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(true);
        when(messageRepository.findByConversationIdOrderByCreatedAtDesc(eq(CONVERSATION_ID), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        // History access should be allowed
        service.history(STUDENT_ID, CONVERSATION_ID, org.springframework.data.domain.Pageable.unpaged());
        verify(messageRepository).findByConversationIdOrderByCreatedAtDesc(eq(CONVERSATION_ID), any());

        // Send gate rejects (no active subscription, maybe only pending or failed renewal)
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatus(STUDENT_ID, COACH_PROFILE_ID, SubscriptionStatus.ACTIVE))
                .thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.sendMessage(STUDENT_ID, CONVERSATION_ID, "content"));
        assertThat(ex.getErrorCode()).isEqualTo("MESSAGING_NOT_ALLOWED");
    }

    @Test
    void checkParticipant_studentWithHistoryAccess_returnsTrue() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(true);

        boolean isPart = service.isParticipant(STUDENT_ID, CONVERSATION_ID);
        assertThat(isPart).isTrue();
    }

    @Test
    void checkParticipant_studentWithoutHistoryAccess_returnsFalse() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(subscriptionRepository.existsHistoryAccessSubscription(STUDENT_ID, COACH_PROFILE_ID)).thenReturn(false);

        boolean isPart = service.isParticipant(STUDENT_ID, CONVERSATION_ID);
        assertThat(isPart).isFalse();
    }
}
