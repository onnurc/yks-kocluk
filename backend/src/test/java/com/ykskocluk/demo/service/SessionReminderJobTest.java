package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.SessionReminderProperties;
import com.ykskocluk.demo.dto.SessionReminderView;
import com.ykskocluk.demo.integration.MailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionReminderJobTest {

    @Mock SessionService sessionService;
    @Mock MailClient mailClient;

    SessionReminderJob job;
    Instant now;

    @BeforeEach
    void setUp() {
        job = new SessionReminderJob(sessionService, mailClient, new SessionReminderProperties(Duration.ofHours(24)));
        now = Instant.parse("2026-08-20T10:00:00Z");
    }

    @Test
    void runReminders_claimsAndSendsForEachCandidate() {
        SessionReminderView view = new SessionReminderView(1L, "student@example.com", "Ayşe Koç",
                now.plusSeconds(3600), "https://meet.jit.si/x");
        when(sessionService.findReminderCandidates(now, now.plus(Duration.ofHours(24))))
                .thenReturn(List.of(view));
        when(sessionService.claimReminder(1L, now)).thenReturn(true);

        job.runReminders(now);

        verify(mailClient).sendSessionReminder("student@example.com", "Ayşe Koç", view.startTime(),
                "https://meet.jit.si/x");
    }

    /**
     * The regression guard for the manual-link era: with {@code app.meet-link.enabled} off no
     * session ever carries a link, so a link-less candidate must still be reminded — the mail
     * simply omits the join block. If this ever stops holding, nobody gets a reminder at all.
     */
    @Test
    void runReminders_candidateWithoutMeetLink_stillSendsReminder() {
        SessionReminderView noLink = new SessionReminderView(1L, "student@example.com", "Ayşe Koç",
                now.plusSeconds(3600), null);
        when(sessionService.findReminderCandidates(now, now.plus(Duration.ofHours(24))))
                .thenReturn(List.of(noLink));
        when(sessionService.claimReminder(1L, now)).thenReturn(true);

        job.runReminders(now);

        verify(mailClient).sendSessionReminder("student@example.com", "Ayşe Koç", noLink.startTime(), null);
    }

    @Test
    void runReminders_claimLost_skipsMail() {
        SessionReminderView view = new SessionReminderView(1L, "student@example.com", "Ayşe Koç",
                now.plusSeconds(3600), "https://meet.jit.si/x");
        when(sessionService.findReminderCandidates(any(), any())).thenReturn(List.of(view));
        when(sessionService.claimReminder(1L, now)).thenReturn(false); // another run already claimed it

        job.runReminders(now);

        verify(mailClient, never()).sendSessionReminder(any(), any(), any(), any());
    }

    @Test
    void runReminders_oneFailureDoesNotStopTheBatch() {
        SessionReminderView failing = new SessionReminderView(1L, "a@example.com", "Coach A",
                now.plusSeconds(3600), "https://meet.jit.si/a");
        SessionReminderView ok = new SessionReminderView(2L, "b@example.com", "Coach B",
                now.plusSeconds(7200), "https://meet.jit.si/b");
        when(sessionService.findReminderCandidates(any(), any())).thenReturn(List.of(failing, ok));
        when(sessionService.claimReminder(1L, now)).thenThrow(new RuntimeException("DB hiccup"));
        when(sessionService.claimReminder(2L, now)).thenReturn(true);

        job.runReminders(now);

        verify(mailClient, never()).sendSessionReminder(eq("a@example.com"), any(), any(), any());
        verify(mailClient).sendSessionReminder("b@example.com", "Coach B", ok.startTime(), "https://meet.jit.si/b");
    }
}
