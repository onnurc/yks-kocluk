-- V28 — Idempotent reseed/repair of the demo accounts from V14/V15.
--
-- Why this exists: Flyway migrations run exactly once per database (tracked in
-- flyway_schema_history). DemoSeedCleanupComponent unconditionally deletes these demo rows on
-- every boot where app.demo-seed-enabled=false (the default). If that ever fires on a database
-- where V14/V15 already ran, the rows are gone for good — flipping demo-seed-enabled back to
-- true afterwards does NOT bring them back, because Flyway will never re-run V14/V15 again.
-- (This happened on the shared local/dev Neon DB on 2026-08-15 — see handoff.md.)
--
-- This file is the repair path: every statement is written to be safe whether the target rows
-- are completely missing (fresh DB), partially present, or already correct. It also never
-- trusts a column default for email_verified/legal_onboarding_completed — both are set
-- explicitly, since a prior partial recovery could otherwise leave legal_onboarding_completed
-- stuck at its false default and silently block the child-safety message gate.
--
-- Only reachable at all where db/demo-seed is on the Flyway location list — local dev only,
-- never the default (see application.yml's flyway.locations comment).

-- 1. Users. uq_users_email lets a plain ON CONFLICT do the idempotency work.
insert into users (email, password_hash, full_name, role, status, email_verified,
                    legal_onboarding_completed, created_at, updated_at, version)
values
    ('admin.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm',
     'Demo Admin', 'ADMIN', 'ACTIVE', true, true, now(), now(), 0),
    ('student.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm',
     'Demo Student', 'STUDENT', 'ACTIVE', true, true, now(), now(), 0),
    ('coach.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm',
     'Demo Coach', 'COACH', 'ACTIVE', true, true, now(), now(), 0),
    ('suspended.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm',
     'Demo Suspended Student', 'STUDENT', 'SUSPENDED', true, true, now(), now(), 0),
    ('student.pending.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm',
     'Demo Pending Student', 'STUDENT', 'ACTIVE', true, true, now(), now(), 0),
    ('student.active.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm',
     'Demo Active Student', 'STUDENT', 'ACTIVE', true, true, now(), now(), 0)
on conflict (email) do nothing;

-- 1b. Belt-and-suspenders for rows that already existed (e.g. from a manual partial recovery):
-- the ON CONFLICT above skips them entirely, so re-assert the two gate-critical flags here.
update users
set email_verified = true,
    legal_onboarding_completed = true
where email in (
    'admin.demo@example.com', 'student.demo@example.com', 'coach.demo@example.com',
    'suspended.demo@example.com', 'student.pending.demo@example.com', 'student.active.demo@example.com'
);

-- 2. Coach profile for the demo coach. uq_coach_profiles_user (unique on user_id) backs the
-- ON CONFLICT.
insert into coach_profiles (user_id, headline, bio, university_id, department, graduation_year,
                             status, active_student_count, max_student_capacity,
                             payout_account_ready, created_at, updated_at, version)
select id, 'YKS Derece Koçu', 'Sayısal alanda derece yapmış tecrübeli YKS koçu.', 1,
       'Bilgisayar Mühendisliği', 2024, 'APPROVED', 0, 10, true, now(), now(), 0
from users
where email = 'coach.demo@example.com'
on conflict (user_id) do nothing;

-- 3. Subscriptions. No unique constraint on (student_user_id, coach_profile_id) — a student can
-- legitimately have more than one subscription row over time — so WHERE NOT EXISTS is the
-- idempotency guard here instead of ON CONFLICT.
insert into subscriptions (student_user_id, coach_profile_id, package_id, status, start_at,
                            end_at, auto_renew, failed_charge_count, created_at, updated_at, version)
select u.id, cp.id, 1, 'PENDING_PAYMENT', now(), now() + interval '30 days', true, 0, now(), now(), 0
from users u
join coach_profiles cp on cp.user_id = (select id from users where email = 'coach.demo@example.com')
where u.email = 'student.pending.demo@example.com'
  and not exists (
      select 1 from subscriptions s
      where s.student_user_id = u.id and s.coach_profile_id = cp.id
  );

insert into subscriptions (student_user_id, coach_profile_id, package_id, status, start_at,
                            end_at, auto_renew, failed_charge_count, created_at, updated_at, version)
select u.id, cp.id, 2, 'ACTIVE', now(), now() + interval '30 days', true, 0, now(), now(), 0
from users u
join coach_profiles cp on cp.user_id = (select id from users where email = 'coach.demo@example.com')
where u.email = 'student.active.demo@example.com'
  and not exists (
      select 1 from subscriptions s
      where s.student_user_id = u.id and s.coach_profile_id = cp.id
  );

-- 4. Payments. uq_payments_idempotency_key backs the ON CONFLICT. The SELECT resolves the
-- subscription whether it was just inserted above or already existed, so this is safe either way.
insert into payments (subscription_id, type, amount, status, idempotency_key, commission_rate,
                       commission_amount, coach_payout_amount, created_at, updated_at, version)
select s.id, 'CHARGE', 1500.00, 'PENDING', 'seed:pending:student', 0.2000, 300.00, 1200.00,
       now(), now(), 0
from subscriptions s
join users u on s.student_user_id = u.id
where u.email = 'student.pending.demo@example.com'
on conflict (idempotency_key) do nothing;

insert into payments (subscription_id, type, amount, status, idempotency_key, provider_reference,
                       commission_rate, commission_amount, coach_payout_amount, created_at,
                       updated_at, version)
select s.id, 'CHARGE', 2500.00, 'SUCCESS', 'seed:active:student', 'seeded_reference_active_student',
       0.2000, 500.00, 2000.00, now(), now(), 0
from subscriptions s
join users u on s.student_user_id = u.id
where u.email = 'student.active.demo@example.com'
on conflict (idempotency_key) do nothing;

-- 5. Coach capacity — recomputed from the actual ACTIVE subscription count rather than a blind
-- +1 (what V15 did), so this stays correct no matter how many times the statements above have
-- already run against this database.
update coach_profiles cp
set active_student_count = (
    select count(*) from subscriptions s
    join users u on s.student_user_id = u.id
    where s.coach_profile_id = cp.id and s.status = 'ACTIVE'
)
where cp.user_id = (select id from users where email = 'coach.demo@example.com');
