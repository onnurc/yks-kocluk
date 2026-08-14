package com.ykskocluk.demo.service;

import com.ykskocluk.demo.integration.MailClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PurchaseConfirmationMailListenerTest {

    @Mock MailClient mailClient;

    @InjectMocks PurchaseConfirmationMailListener listener;

    @Test
    void onPurchaseConfirmed_sendsBothStudentAndCoachMail() {
        Instant purchasedAt = Instant.parse("2026-08-01T10:00:00Z");
        Instant periodEndAt = Instant.parse("2026-08-31T10:00:00Z");
        PurchaseConfirmedEvent event = new PurchaseConfirmedEvent(7L, "student@example.com", "Ali",
                "Aylık Koçluk", "coach@example.com", "Ayşe Koç", new BigDecimal("1500.00"), "TRY",
                purchasedAt, periodEndAt);

        listener.onPurchaseConfirmed(event);

        verify(mailClient).sendPurchaseConfirmed("student@example.com", "Ali", "Aylık Koçluk",
                "Ayşe Koç", new BigDecimal("1500.00"), "TRY", purchasedAt, periodEndAt);
        verify(mailClient).sendPurchaseConfirmedToCoach("coach@example.com", "Ayşe Koç", "Ali",
                "Aylık Koçluk", purchasedAt);
    }
}
