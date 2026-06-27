package com.ykskocluk.demo.enums;

/**
 * Lifecycle of a booked coaching session. Quota-consuming statuses are
 * {@code PLANNED, COMPLETED, LATE_CANCELLED, NO_SHOW} (the full set, as of the Phase 4d
 * cancellation lifecycle — see {@code SessionService.QUOTA_STATUSES}). {@code CANCELLED}
 * (early cancel) frees the slot and does not consume quota.
 */
public enum SessionStatus {
    PLANNED,
    COMPLETED,
    CANCELLED,
    LATE_CANCELLED,
    NO_SHOW
}
