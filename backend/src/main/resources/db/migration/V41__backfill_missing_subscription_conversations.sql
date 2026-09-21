-- V41 — Backfill (student, coach) conversations for subscriptions that reached ACTIVE before
-- automatic conversation creation existed (SubscriptionService.completePaymentSuccess now calls
-- MessageService.ensureConversationForActiveSubscription in the same transaction). Before this
-- fix, a Conversation only came into existence when the student clicked "Mesaj Gönder" at least
-- once — a coach had no channel to reach an already-paying student who never did, which mattered
-- once automatic Google Meet link generation was turned off (link now only travels over chat).
--
-- `purchased_at is not null` is the exact signal for "this subscription's activation went
-- through completePaymentSuccess" — it is set nowhere else in the codebase, so it is a more
-- precise predicate than current status (an admin can terminate a PENDING_PAYMENT subscription
-- straight to TERMINATED without it ever having been ACTIVE).
--
-- GROUP BY collapses multiple subscriptions for the same pair (cancel + resubscribe) to the one
-- row the UNIQUE(student_user_id, coach_profile_id) constraint (uq_conversation_pair, V9) allows;
-- MIN(purchased_at) backfills last_message_at from the earliest real activation, the same
-- nearest-available-timestamp approach V25 used for payments.succeeded_at. WHERE NOT EXISTS keeps
-- this safe to run again against a database where some pairs already have a conversation.
insert into conversations (student_user_id, coach_profile_id, last_message_at, created_at, updated_at, version)
select s.student_user_id, s.coach_profile_id, min(s.purchased_at), now(), now(), 0
from subscriptions s
where s.purchased_at is not null
  and not exists (
    select 1 from conversations c
    where c.student_user_id = s.student_user_id
      and c.coach_profile_id = s.coach_profile_id
  )
group by s.student_user_id, s.coach_profile_id;
