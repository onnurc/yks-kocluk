alter table coach_availabilities
    add column purpose varchar(20) not null default 'PAID';

alter table coach_availabilities
    add constraint ck_coach_availability_purpose check (purpose in ('PAID', 'TRIAL'));

create index idx_availabilities_trial_window
    on coach_availabilities (coach_profile_id, start_time)
    where purpose = 'TRIAL';

alter table trial_consultations
    add column meeting_url varchar(1000),
    add column confirmed_at timestamp with time zone,
    add column confirmed_by bigint references users(id);

drop index uq_active_trial_per_student_coach;

create unique index uq_active_trial_per_student_coach
    on trial_consultations(student_user_id, coach_profile_id)
    where status in ('REQUESTED', 'CONFIRMED');

create index idx_trial_consultations_student_rolling_window
    on trial_consultations(student_user_id, start_time)
    where status <> 'CANCELLED';
