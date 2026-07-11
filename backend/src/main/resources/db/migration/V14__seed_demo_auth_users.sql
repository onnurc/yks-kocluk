-- V14 — Seed demo users for local/manual frontend authentication tests.
-- Plaintext password for all demo accounts: Password123!
-- Hashed using the project's standard BCrypt scheme.

-- 1. Demo Admin
insert into users (email, password_hash, full_name, role, status, email_verified, created_at, updated_at, version)
select 'admin.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm', 'Demo Admin', 'ADMIN', 'ACTIVE', true, now(), now(), 0
where not exists (select 1 from users where email = 'admin.demo@example.com');

-- 2. Demo Student
insert into users (email, password_hash, full_name, role, status, email_verified, created_at, updated_at, version)
select 'student.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm', 'Demo Student', 'STUDENT', 'ACTIVE', true, now(), now(), 0
where not exists (select 1 from users where email = 'student.demo@example.com');

-- 3. Demo Coach
insert into users (email, password_hash, full_name, role, status, email_verified, created_at, updated_at, version)
select 'coach.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm', 'Demo Coach', 'COACH', 'ACTIVE', true, now(), now(), 0
where not exists (select 1 from users where email = 'coach.demo@example.com');

-- 4. Demo Suspended Student
insert into users (email, password_hash, full_name, role, status, email_verified, created_at, updated_at, version)
select 'suspended.demo@example.com', '$2a$10$dj2f/1xwn4ah9Hx9gCCCwO9pUINWDfHqFRf16kle6qoZ/PjHqzDNm', 'Demo Suspended Student', 'STUDENT', 'SUSPENDED', true, now(), now(), 0
where not exists (select 1 from users where email = 'suspended.demo@example.com');

-- 5. Coach Profile for the Demo Coach
insert into coach_profiles (user_id, headline, bio, university_id, department, graduation_year, status, active_student_count, max_student_capacity, payout_account_ready, created_at, updated_at, version)
select id, 'YKS Derece Koçu', 'Sayısal alanda derece yapmış tecrübeli YKS koçu.', 1, 'Bilgisayar Mühendisliği', 2024, 'APPROVED', 0, 10, true, now(), now(), 0
from users
where email = 'coach.demo@example.com'
  and not exists (
      select 1 from coach_profiles cp join users u on cp.user_id = u.id where u.email = 'coach.demo@example.com'
  );
