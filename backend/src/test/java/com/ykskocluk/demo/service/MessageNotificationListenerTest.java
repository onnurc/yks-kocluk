package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MessageNotificationProperties;
import com.ykskocluk.demo.dto.MessageResponse;
import com.ykskocluk.demo.integration.MailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The email-suppression rules — the point of the feature is that a busy chat does not become an
 * inbox full of mail. Presence and debounce are decided here; that the debounce claim is atomic
 * is a DB behaviour, covered in {@code MessageNotificationClaimTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageNotificationListenerTest {

    private static final long RECIPIENT_ID = 42L;
    private static final long CONVERSATION_ID = 7L;
    private static final Duration DEBOUNCE = Duration.ofMinutes(30);
    private static final String SECRET_CONTENT = "gizli mesaj icerigi";

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock SimpUserRegistry userRegistry;
    @Mock MessageService messageService;
    @Mock MailClient mailClient;
    @Mock SimpUser connectedUser;

    MessageNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new MessageNotificationListener(messagingTemplate, userRegistry, messageService,
                mailClient, new MessageNotificationProperties(DEBOUNCE, "https://app.test"),
                JsonMapper.builder().build());
        when(messageService.unreadTotal(RECIPIENT_ID)).thenReturn(3L);
        when(messageService.claimEmailNotification(anyLong(), anyBoolean(), any(), any())).thenReturn(true);
    }

    @Test
    void offlineRecipientWithNoRecentNotice_getsMailWithLinkAndNoMessageText() {
        offline();

        listener.onMessageSent(event(true));

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sender = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailClient).sendNewMessageNotification(to.capture(), sender.capture(), link.capture());

        assertThat(to.getValue()).isEqualTo("recipient@test.com");
        assertThat(sender.getValue()).isEqualTo("Gonderen Kisi");
        assertThat(link.getValue()).isEqualTo("https://app.test/messages/" + CONVERSATION_ID);
        // If the message body ever reaches an argument of this call, the privacy decision behind
        // the feature has been reversed by accident — see MailClient.sendNewMessageNotification.
        assertThat(to.getValue() + sender.getValue() + link.getValue()).doesNotContain(SECRET_CONTENT);
    }

    @Test
    void connectedRecipient_getsNoMail_becauseTheBadgeAlreadyToldThem() {
        online();

        listener.onMessageSent(event(true));

        verifyNoInteractions(mailClient);
        // Presence short-circuits before the claim, so the window is left untouched and the first
        // message after they close the app can still mail immediately.
        verify(messageService, never()).claimEmailNotification(anyLong(), anyBoolean(), any(), any());
    }

    @Test
    void offlineRecipientInsideDebounceWindow_getsNoSecondMail() {
        offline();
        when(messageService.claimEmailNotification(anyLong(), anyBoolean(), any(), any())).thenReturn(false);

        listener.onMessageSent(event(true));

        verifyNoInteractions(mailClient);
    }

    @Test
    void claimTargetsTheRecipientsOwnColumnWithTheConfiguredWindow() {
        offline();
        Instant beforeCall = Instant.now();

        listener.onMessageSent(event(true));

        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> threshold = ArgumentCaptor.forClass(Instant.class);
        // recipientIsStudent = true -> the student column, so a mail to the coach can never
        // silence one owed to the student.
        verify(messageService).claimEmailNotification(eq(CONVERSATION_ID), eq(true),
                now.capture(), threshold.capture());

        assertThat(now.getValue()).isAfterOrEqualTo(beforeCall);
        assertThat(Duration.between(threshold.getValue(), now.getValue())).isEqualTo(DEBOUNCE);
    }

    @Test
    void recipientWithoutConversationAccess_getsNeitherPushNorMail() {
        offline();

        listener.onMessageSent(event(false));

        verifyNoInteractions(mailClient);
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any(Object.class));
        // The thread itself still broadcasts — whoever *can* see it should see the message.
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations/" + CONVERSATION_ID), any(Object.class));
    }

    @Test
    void brokerFailureStillLetsTheMailThrough() {
        offline();
        doThrow(new RuntimeException("broker down"))
                .when(messagingTemplate).convertAndSend(any(String.class), any(Object.class));

        listener.onMessageSent(event(true));

        // Each delivery is isolated: a dead broker must not cost the recipient the one channel
        // that still reaches them.
        verify(mailClient).sendNewMessageNotification(any(), any(), any());
    }

    private void offline() {
        when(userRegistry.getUser(String.valueOf(RECIPIENT_ID))).thenReturn(null);
    }

    private void online() {
        when(userRegistry.getUser(String.valueOf(RECIPIENT_ID))).thenReturn(connectedUser);
    }

    private MessageSentEvent event(boolean recipientHasAccess) {
        MessageResponse message = new MessageResponse(1L, CONVERSATION_ID, 9L, "Gonderen Kisi",
                SECRET_CONTENT, Instant.now(), null);
        return new MessageSentEvent(message, RECIPIENT_ID, "recipient@test.com", true,
                recipientHasAccess, "Gonderen Kisi");
    }
}
