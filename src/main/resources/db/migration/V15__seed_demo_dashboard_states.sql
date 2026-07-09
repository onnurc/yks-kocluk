-- V15 — Seed deterministic student dashboard states for manual testing.
-- Plaintext password for all demo accounts: Password123!

-- 1. Create student.pending.demo@example.com
insert into users (email, password_hash, full_name, role, status, email_verified, created_at, updated_at, version)
select 'student.pending.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm', 'Demo Pending Student', 'STUDENT', 'ACTIVE', true, now(), now(), 0
where not exists (select 1 from users where email = 'student.pending.demo@example.com');

-- 2. Create student.active.demo@example.com
insert into users (email, password_hash, full_name, role, status, email_verified, created_at, updated_at, version)
select 'student.active.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm', 'Demo Active Student', 'STUDENT', 'ACTIVE', true, now(), now(), 0
where not exists (select 1 from users where email = 'student.active.demo@example.com');

-- 3. Seed PENDING_PAYMENT subscription for student.pending.demo@example.com
insert into subscriptions (student_user_id, coach_profile_id, package_id, status, start_at, end_at, auto_renew, failed_charge_count, created_at, updated_at, version)
select
    u.id,
    cp.id,
    1, -- Package: Aylık 1x (1500.00 TRY)
    'PENDING_PAYMENT',
    now(),
    now() + interval '30 days',
    true,
    0,
    now(),
    now(),
    0
from users u
join coach_profiles cp on cp.user_id = (select id from users where email = 'coach.demo@example.com')
where u.email = 'student.pending.demo@example.com'
  and not exists (
      select 1 from subscriptions s
      where s.student_user_id = u.id
        and s.coach_profile_id = cp.id
  );

-- 4. Seed PENDING payment for the pending subscription
insert into payments (subscription_id, type, amount, status, idempotency_key, commission_rate, commission_amount, coach_payout_amount, created_at, updated_at, version)
select
    s.id,
    'CHARGE',
    1500.00,
    'PENDING',
    'seed:pending:student',
    0.2000,
    300.00,
    1200.00,
    now(),
    now(),
    0
from subscriptions s
join users u on s.student_user_id = u.id
where u.email = 'student.pending.demo@example.com'
  and not exists (
      select 1 from payments where idempotency_key = 'seed:pending:student'
  );

-- 5. Seed ACTIVE subscription for student.active.demo@example.com
insert into subscriptions (student_user_id, coach_profile_id, package_id, status, start_at, end_at, auto_renew, failed_charge_count, created_at, updated_at, version)
select
    u.id,
    cp.id,
    2, -- Package: Aylık 2x (2500.00 TRY)
    'ACTIVE',
    now(),
    now() + interval '30 days',
    true,
    0,
    now(),
    now(),
    0
from users u
join coach_profiles cp on cp.user_id = (select id from users where email = 'coach.demo@example.com')
where u.email = 'student.active.demo@example.com'
  and not exists (
      select 1 from subscriptions s
      where s.student_user_id = u.id
        and s.coach_profile_id = cp.id
  );

-- 6. Seed SUCCEEDED payment for the active subscription
insert into payments (subscription_id, type, amount, status, idempotency_key, provider_reference, commission_rate, commission_amount, coach_payout_amount, created_at, updated_at, version)
select
    s.id,
    'CHARGE',
    2500.00,
    'SUCCESS',
    'seed:active:student',
    'seeded_reference_active_student',
    0.2000,
    500.00,
    2000.00,
    now(),
    now(),
    0
from subscriptions s
join users u on s.student_user_id = u.id
where u.email = 'student.active.demo@example.com'
  and not exists (
      select 1 from payments where idempotency_key = 'seed:active:student'
  );

-- 7. Update coach profile capacity count to reflect the active student
update coach_profiles
set active_student_count = active_student_count + 1
where user_id = (select id from users where email = 'coach.demo@example.com')
  -- Only increment if we just successfully inserted the active subscription
  and exists (
      select 1 from subscriptions s
      join users u on s.student_user_id = u.id
      where u.email = 'student.active.demo@example.com'
        and s.status = 'ACTIVE'
  );
