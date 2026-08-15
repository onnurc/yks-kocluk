-- Reminder dispatch marker for SessionReminderJob. Nullable + set-once: the job claims a
-- session atomically via "update ... where reminder_sent_at is null", so a null value is the
-- idempotency guard against double-sends across overlapping/concurrent job runs.
alter table sessions add column reminder_sent_at timestamptz;
