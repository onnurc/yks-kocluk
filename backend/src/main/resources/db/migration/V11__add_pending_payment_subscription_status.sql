-- V11 -- Keep duplicate protection race-safe for pending-payment subscriptions.
-- PENDING_PAYMENT is not access-granting, but a student must not create unlimited duplicate
-- pending rows for the same (student, coach) pair.

drop index uq_live_subscription_per_coach;

create unique index uq_live_subscription_per_coach
    on subscriptions (student_user_id, coach_profile_id)
    where status in ('ACTIVE', 'PAST_DUE', 'PENDING_PAYMENT');