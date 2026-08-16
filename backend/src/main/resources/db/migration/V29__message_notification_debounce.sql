-- Email-notification debounce markers for new chat messages (MessageNotificationListener).
-- Same shape as sessions.reminder_sent_at (V27): the listener claims the right to send with an
-- atomic "update ... where <col> is null or <col> < :threshold", so the column is the debounce
-- window AND the idempotency guard in one — concurrent sends can never both win the claim.
--
-- Two columns, not one: a conversation has two directions. The student writes -> the coach is
-- notified; the coach writes -> the student is notified. A single shared column would let a mail
-- to the coach silence an unrelated mail to the student for the whole window.
--
-- Deliberately columns on conversations rather than a message_notifications table: the debounce
-- is per (conversation, recipient) and a conversation has exactly two participants, so a table
-- would only add an entity + repository for the same two values (CLAUDE.md: no over-engineering).
--
-- Numbered V29 because V28 is taken by db/demo-seed/V28__reseed_demo_users.sql — demo-seed shares
-- this version space (see the flyway.locations allowlist comment in application.yml).
alter table conversations add column student_notified_at timestamptz;
alter table conversations add column coach_notified_at timestamptz;
