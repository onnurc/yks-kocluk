package com.ykskocluk.demo.service;

import com.ykskocluk.demo.integration.MailClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionCancellationMailListenerTest {

    @Mock MailClient mailClient;

    @InjectMocks SessionCancellationMailListener listener;

    @Test
    void onSessionCancelled_sendsBothStudentAndCoachMail() {
        Instant startTime = Instant.parse("2026-08-20T10:00:00Z");
        SessionCancelledEvent event = new SessionCancelledEvent(42L, "student@example.com", "Ali",
                "coach@example.com", "Ayşe Koç", startTime, false);

        listener.onSessionCancelled(event);

        verify(mailClient).sendSessionCancelled("student@example.com", "Ayşe Koç", startTime, false);
        verify(mailClient).sendSessionCancelledToCoach("coach@example.com", "Ayşe Koç", "Ali", startTime, false);
    }
}
