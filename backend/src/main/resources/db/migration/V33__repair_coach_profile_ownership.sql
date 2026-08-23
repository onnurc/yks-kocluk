alter table coach_profiles
    alter column headline drop not null,
    alter column university_id drop not null;

insert into coach_profiles (
    user_id, headline, bio, university_id, department, graduation_year,
    status, active_student_count, max_student_capacity, payout_account_ready,
    created_at, updated_at, version
)
select
    u.id, null, null, null, null, null,
    'PENDING', 0, 10, false,
    now(), now(), 0
from users u
where u.role = 'COACH'
  and not exists (select 1 from coach_profiles cp where cp.user_id = u.id);
