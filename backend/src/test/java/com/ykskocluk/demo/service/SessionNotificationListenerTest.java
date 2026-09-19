package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MeetLinkProperties;
import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.integration.MeetClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionNotificationListenerTest {

    @Mock MeetClient meetClient;
    @Mock MailClient mailClient;
    @Mock SessionService sessionService;

    /** The flag is the thing under test, so each test builds the listener with its own value. */
    private SessionNotificationListener listener(boolean meetLinkEnabled) {
        return new SessionNotificationListener(meetClient, mailClient, sessionService,
                new MeetLinkProperties(meetLinkEnabled));
    }

    private SessionBookedEvent event() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        return new SessionBookedEvent(42L, "student@example.com", "Student", "coach@example.com", "Coach",
                start, start.plus(1, ChronoUnit.HOURS));
    }

    @Test
    void onSessionBooked_meetLinkEnabled_setsMeetLink_andSendsMail() {
        when(meetClient.createMeetLink(eq(42L), any(), any())).thenReturn("https://meet.stub.local/session/42");

        listener(true).onSessionBooked(event());

        verify(sessionService).setMeetLink(42L, "https://meet.stub.local/session/42");
        verify(mailClient).sendSessionBooked(eq("student@example.com"), eq("Coach"), any(),
                eq("https://meet.stub.local/session/42"));
        verify(mailClient).sendSessionBookedToCoach(eq("coach@example.com"), eq("Coach"), eq("Student"), any(),
                eq("https://meet.stub.local/session/42"));
    }

    @Test
    void onSessionBooked_meetLinkDisabled_skipsGeneration_butStillMails() {
        listener(false).onSessionBooked(event());

        // No client call and no DB write — the coach shares a Google Meet link over chat instead.
        verifyNoInteractions(meetClient);
        verify(sessionService, never()).setMeetLink(anyLong(), any());
        // The mails still go out, carrying no link (the templates omit the join block then).
        verify(mailClient).sendSessionBooked(eq("student@example.com"), eq("Coach"), any(), isNull());
        verify(mailClient).sendSessionBookedToCoach(eq("coach@example.com"), eq("Coach"), eq("Student"), any(),
                isNull());
    }

    @Test
    void onSessionBooked_meetClientThrows_isSwallowed_bookingNotAffected() {
        when(meetClient.createMeetLink(anyLong(), any(), any()))
                .thenThrow(new RuntimeException("Meet API down"));

        // The booking already committed — a side-effect failure must never propagate.
        assertThatNoException().isThrownBy(() -> listener(true).onSessionBooked(event()));

        verify(sessionService, never()).setMeetLink(anyLong(), any());
        verify(mailClient, never()).sendSessionBooked(any(), any(), any(), any());
        verify(mailClient, never()).sendSessionBookedToCoach(any(), any(), any(), any(), any());
    }
}
