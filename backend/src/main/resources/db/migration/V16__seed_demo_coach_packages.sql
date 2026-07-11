-- V16 — Seed additional package 'Aylık 4x' for testing.
insert into packages (name, weekly_sessions, duration_days, price, active, created_at, updated_at, version)
select 'Aylık 4x', 4, 30, 4000.00, true, now(), now(), 0
where not exists (select 1 from packages where name = 'Aylık 4x');
