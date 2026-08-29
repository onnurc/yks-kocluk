package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.enums.ReportStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stub mail client — logs instead of sending. Active under the {@code test} profile only;
 * {@link ResendMailClient} (the real Phase 7 impl) is active under every other profile.
 * Mutually exclusive profiles → exactly one {@link MailClient} bean, no ambiguity.
 */
@Component
@Profile("test")
public class StubMailClient implements MailClient {

    private static final Logger log = LoggerFactory.getLogger(StubMailClient.class);

    @Override
    public void sendPurchaseConfirmed(String toEmail, String studentName, String packageName,
                                      String coachName, BigDecimal amount, String currency,
                                      Instant purchasedAt, Instant periodEndAt) {
        log.info("[STUB MailClient] purchase-confirmed mail to {} (package {}, coach {}, amount {} {})",
                toEmail, packageName, coachName, amount, currency);
    }

    @Override
    public void sendPurchaseConfirmedToCoach(String toEmail, String coachName, String studentName,
                                             String packageName, Instant purchasedAt) {
        log.info("[STUB MailClient] purchase-confirmed (coach side) mail to {} (student {}, package {})",
                toEmail, studentName, packageName);
    }

    @Override
    public void sendSessionBooked(String toEmail, String coachName, Instant startTime, String meetLink) {
        log.info("[STUB MailClient] session-booked mail to {} (coach {}, start {}, link {})",
                toEmail, coachName, startTime, meetLink);
    }

    @Override
    public void sendSessionBookedToCoach(String toEmail, String coachName, String studentName,
                                         Instant startTime, String meetLink) {
        log.info("[STUB MailClient] session-booked (coach side) mail to {} (student {}, start {}, link {})",
                toEmail, studentName, startTime, meetLink);
    }

    @Override
    public void sendSessionCancelled(String toEmail, String coachName, Instant startTime, boolean late) {
        log.info("[STUB MailClient] session-cancelled mail to {} (coach {}, start {}, late {})",
                toEmail, coachName, startTime, late);
    }

    @Override
    public void sendSessionCancelledToCoach(String toEmail, String coachName, String studentName,
                                            Instant startTime, boolean late) {
        log.info("[STUB MailClient] session-cancelled (coach side) mail to {} (student {}, start {}, late {})",
                toEmail, studentName, startTime, late);
    }

    @Override
    public void sendSessionReminder(String toEmail, String coachName, Instant startTime, String meetLink) {
        log.info("[STUB MailClient] session-reminder mail to {} (coach {}, start {})",
                toEmail, coachName, startTime);
    }

    @Override
    public void sendTrialConsultationConfirmed(String toEmail, String coachName, Instant startTime, String meetingLink) {
        log.info("[STUB MailClient] trial-confirmed mail to {} (coach {}, startTime {})",
                toEmail, coachName, startTime);
    }

    @Override
    public void sendNewMessageNotification(String toEmail, String senderName, String conversationLink) {
        // Logs the sender and the link, never any message content — same reason the real mail
        // carries none (see MailClient's javadoc); logs are not a loophole around that.
        log.info("[STUB MailClient] new-message mail to {} (from {}, link {})",
                toEmail, senderName, conversationLink);
    }

    @Override
    public void sendRenewalSucceeded(String toEmail, String coachName, Instant nextEndAt, BigDecimal amount) {
        log.info("[STUB MailClient] renewal-succeeded mail to {} (coach {}, nextEnd {}, amount {})",
                toEmail, coachName, nextEndAt, amount);
    }

    @Override
    public void sendPaymentFailed(String toEmail, String coachName, int attemptNumber, int maxAttempts) {
        log.info("[STUB MailClient] payment-failed mail to {} (coach {}, attempt {}/{})",
                toEmail, coachName, attemptNumber, maxAttempts);
    }

    @Override
    public void sendSubscriptionExpired(String toEmail, String coachName) {
        log.info("[STUB MailClient] subscription-expired mail to {} (coach {})", toEmail, coachName);
    }

    @Override
    public void sendCancellationConfirmed(String toEmail, String coachName, Instant accessUntil) {
        log.info("[STUB MailClient] cancellation-confirmed mail to {} (coach {}, accessUntil {})",
                toEmail, coachName, accessUntil);
    }

    @Override
    public void sendReportReceived(String toEmail) {
        log.info("[STUB MailClient] report-received mail to {}", toEmail);
    }

    @Override
    public void sendReportStatusUpdated(String toEmail, ReportStatus newStatus) {
        log.info("[STUB MailClient] report-status-updated mail to {} (newStatus {})", toEmail, newStatus);
    }

    @Override
    public void sendUserSuspended(String toEmail) {
        log.info("[STUB MailClient] user-suspended mail to {}", toEmail);
    }

    @Override
    public void sendPasswordReset(String toEmail, String resetLink) {
        log.info("[STUB MailClient] password-reset mail to {}", toEmail);
    }

    @Override
    public void sendEmailVerification(String toEmail, String code) {
        // The code is intentionally never logged, even by the local/test stub.
        log.info("[STUB MailClient] email-verification mail to {}", toEmail);
    }

    @Override
    public void sendWelcome(String toEmail, String fullName) {
        log.info("[STUB MailClient] welcome mail to {} ({})", toEmail, fullName);
    }
}
