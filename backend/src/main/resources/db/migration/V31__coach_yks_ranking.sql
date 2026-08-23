alter table coach_profiles
    add column yks_ranking integer;

alter table coach_profiles
    add constraint chk_coach_profiles_yks_ranking_positive
        check (yks_ranking is null or yks_ranking > 0);
