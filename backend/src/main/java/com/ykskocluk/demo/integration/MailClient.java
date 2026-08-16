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

    /**
     * Purchase confirmed — coach side ("you have a new subscriber"). Deliberately carries no
     * payment amount: coach payout math is unresolved (see docs/adr_coach_payout_architecture.md),
     * so commission-sensitive figures are not surfaced here.
     */
    void sendPurchaseConfirmedToCoach(String toEmail, String coachName, String studentName,
                                      String packageName, Instant purchasedAt);

    void sendSessionBooked(String toEmail, String coachName, Instant startTime, String meetLink);

    /** Session booked — coach side ("you have a new session on your calendar"). */
    void sendSessionBookedToCoach(String toEmail, String coachName, String studentName,
                                  Instant startTime, String meetLink);

    /**
     * Student cancelled their own session — confirmation to the student. {@code late} = within
     * the 24h window (slot stayed booked, quota burned) vs. an early cancel (slot reopened,
     * quota returned). Only the student-initiated cancel flow exists today — no coach-initiated
     * cancellation.
     */
    void sendSessionCancelled(String toEmail, String coachName, Instant startTime, boolean late);

    /** Student cancelled their own session — notification to the coach. */
    void sendSessionCancelledToCoach(String toEmail, String coachName, String studentName,
                                     Instant startTime, boolean late);

    /**
     * Reminder ~24h before a PLANNED session — reduces no-shows. Student side only: coaches are
     * paid via the subscription regardless of a no-show, so the reminder's purpose (get the
     * student to show up) doesn't apply to them.
     */
    void sendSessionReminder(String toEmail, String coachName, Instant startTime, String meetLink);

    /** Renewal charged successfully — informational (next billing date + amount). */
    void sendRenewalSucceeded(String toEmail, String coachName, Instant nextEndAt, BigDecimal amount);

    /** A renewal charge failed but the subscription is in grace — fires on each failed retry. */
    void sendPaymentFailed(String toEmail, String coachName, int attemptNumber, int maxAttempts);

    /** Subscription has ended — retries exhausted, or a cancelled sub reached its end date. */
    void sendSubscriptionExpired(String toEmail, String coachName);

    /** Cancellation confirmed — auto-renew is off; access continues until {@code accessUntil}. */
    void sendCancellationConfirmed(String toEmail, String coachName, Instant accessUntil);

    /**
     * "You have a new message" — sent only when the recipient has no live WebSocket session and
     * hasn't already been emailed about this conversation inside the debounce window (see
     * {@code MessageNotificationListener.shouldSendEmail}).
     *
     * <p><strong>Carries no message text, not even a preview — deliberately.</strong> Three
     * reasons: the platform serves minors whose inboxes are frequently shared with or readable by
     * a parent; the whole messaging design routes conversations through an in-platform,
     * admin-observable channel with its own retention and anonymization rules, which mailing the
     * content would quietly bypass; and copying message bodies to a third-party mail provider is
     * exactly the kind of unnecessary spreading of personal data KVKK data minimization asks us
     * not to do. Sender name plus a link back is enough to bring someone to the app.
     */
    void sendNewMessageNotification(String toEmail, String senderName, String conversationLink);

    /** Confirm to the reporter that their safety report was received. */
    void sendReportReceived(String toEmail);

    /** Notify the reporter that their report status has been updated by moderation. */
    void sendReportStatusUpdated(String toEmail, ReportStatus newStatus);

    /** Notify the user that their account has been suspended. */
    void sendUserSuspended(String toEmail);

    void sendPasswordReset(String toEmail, String resetLink);

    /** Transactional ownership-verification code; independent of marketing preferences. */
    void sendEmailVerification(String toEmail, String code);

    /**
     * Fired once, the moment an account first becomes usable — after a successful
     * {@code EmailVerificationService.verify()} for password sign-ups, or immediately on account
     * creation/linking for Google sign-ups (already verified by Google, no code step). Deliberately
     * distinct from {@link #sendEmailVerification}, which only delivers the code and may be sent
     * several times (resend) before the account is ever usable.
     */
    void sendWelcome(String toEmail, String fullName);
}
