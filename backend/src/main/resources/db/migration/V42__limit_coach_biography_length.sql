-- Keep the existing coach_profiles.bio field while aligning its database limit
-- with the public profile and API contract.
update coach_profiles
set bio = left(bio, 1000)
where char_length(bio) > 1000;

alter table coach_profiles
    alter column bio type varchar(1000);
