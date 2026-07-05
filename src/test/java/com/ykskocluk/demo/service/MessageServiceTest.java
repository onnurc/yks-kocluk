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
                coachProfileRepository, userRepository, conversationMapper, messageMapper);

        User coachUser = new User();
        ReflectionTestUtils.setField(coachUser, "id", COACH_USER_ID);
        coach = new CoachProfile();
        ReflectionTestUtils.setField(coach, "id", COACH_PROFILE_ID);
        coach.setUser(coachUser);

        User studentUser = new User();
        ReflectionTestUtils.setField(studentUser, "id", STUDENT_ID);
        conversation = new Conversation();
        ReflectionTestUtils.setField(conversation, "id", CONVERSATION_ID);
        conversation.setStudent(studentUser);
        conversation.setCoachProfile(coach);
        conversation.setLastMessageAt(Instant.now().minusSeconds(3600));

        lenient().when(conversationMapper.toResponse(any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new ConversationResponse(CONVERSATION_ID, COACH_PROFILE_ID, "Coach", "Student",
                        Instant.now(), 0));
    }

    // --- gate (checkpoints 1 & 2) ---

    @Test
    void openConversation_noSubscriptionEver_throws403() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatusNot(
                STUDENT_ID, COACH_PROFILE_ID, SubscriptionStatus.PENDING_PAYMENT))
                .thenReturn(false);

        ApiException ex = catchThrowableOfType(ApiException.class,
                () -> service.openConversation(STUDENT_ID, COACH_PROFILE_ID));
        assertThat(ex.getErrorCode()).isEqualTo("MESSAGING_NOT_ALLOWED");
        assertThat(ex.getStatus().value()).isEqualTo(403);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void openConversation_pastOrActiveSubscription_allowed_createsConversation() {
        when(coachProfileRepository.findById(COACH_PROFILE_ID)).thenReturn(Optional.of(coach));
        when(subscriptionRepository.existsByStudentIdAndCoachProfileIdAndStatusNot(
                STUDENT_ID, COACH_PROFILE_ID, SubscriptionStatus.PENDING_PAYMENT))
                .thenReturn(true); // any status — active or past
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

    // --- mark-read delegates with the reader id (the "only other party" clause lives in the query) ---

    @Test
    void markRead_participant_callsRepoWithReaderId() {
        when(conversationRepository.findById(CONVERSATION_ID)).thenReturn(Optional.of(conversation));
        when(messageRepository.markRead(eq(CONVERSATION_ID), eq(COACH_USER_ID), any())).thenReturn(2);

        int flipped = service.markRead(COACH_USER_ID, CONVERSATION_ID);

        assertThat(flipped).isEqualTo(2);
        verify(messageRepository).markRead(eq(CONVERSATION_ID), eq(COACH_USER_ID), any());
    }
}
