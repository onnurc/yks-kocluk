package com.ykskocluk.demo.integration;

import com.ykskocluk.demo.enums.ReportStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transactional email provider (Resend). One of the four sanctioned external-service interfaces
 * (stub-first). All methods are best-effort and always dispatched <strong>after</strong> the relevant
 * DB transaction has committed (booking listener / renewal job / cancel endpoint) — a mail failure
 * never rolls back state.
 */
public interface MailClient {

    void sendPurchaseConfirmed(String toEmail, String studentName, String packageName,
                               String coachName, BigDecimal amount, String currency,
                               Instant purchasedAt, Instant periodEndAt);

    void sendSessionBooked(String toEmail, String coachName, Instant startTime, String meetLink);

    /** Renewal charged successfully — informational (next billing date + amount). */
    void sendRenewalSucceeded(String toEmail, String coachName, Instant nextEndAt, BigDecimal amount);

    /** A renewal charge failed but the subscription is in grace — fires on each failed retry. */
    void sendPaymentFailed(String toEmail, String coachName, int attemptNumber, int maxAttempts);

    /** Subscription has ended — retries exhausted, or a cancelled sub reached its end date. */
    void sendSubscriptionExpired(String toEmail, String coachName);

    /** Cancellation confirmed — auto-renew is off; access continues until {@code accessUntil}. */
    void sendCancellationConfirmed(String toEmail, String coachName, Instant accessUntil);

    /** Confirm to the reporter that their safety report was received. */
    void sendReportReceived(String toEmail);

    /** Notify the reporter that their report status has been updated by moderation. */
    void sendReportStatusUpdated(String toEmail, ReportStatus newStatus);

    /** Notify the user that their account has been suspended. */
    void sendUserSuspended(String toEmail);

    void sendPasswordReset(String toEmail, String resetLink);

    /** Transactional ownership-verification code; independent of marketing preferences. */
    void sendEmailVerification(String toEmail, String code);
}
