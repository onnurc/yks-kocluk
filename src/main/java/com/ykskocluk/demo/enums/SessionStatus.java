package com.ykskocluk.demo.enums;

/**
 * Lifecycle of a booked coaching session. Quota-consuming statuses are
 * {@code PLANNED, COMPLETED} for now; {@code LATE_CANCELLED, NO_SHOW} also consume
 * quota but only arrive with the cancellation lifecycle in Phase 4d. {@code CANCELLED}
 * (early cancel) frees the slot and does not consume quota.
 */
public enum SessionStatus {
    PLANNED,
    COMPLETED,
    CANCELLED,
    LATE_CANCELLED,
    NO_SHOW
}
