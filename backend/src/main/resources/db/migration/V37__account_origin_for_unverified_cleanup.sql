alter table users
    add column account_origin varchar(30) not null default 'LEGACY';

update users
   set account_origin = 'OAUTH'
 where google_sub is not null;

update users u
   set account_origin = 'COACH_APPLICATION'
 where exists (
       select 1 from coach_applications a
        where a.linked_user_id = u.id and a.status = 'APPROVED'
 );

-- These artifacts are created together only by the public password-registration transaction.
-- Requiring both lets existing genuine registrations enter cleanup without guessing from role or
-- email_verified alone, while seeded/system users remain LEGACY and are retained.
update users u
   set account_origin = 'PUBLIC_PASSWORD'
 where u.role = 'STUDENT'
   and u.email_verified = false
   and u.google_sub is null
   and u.password_hash is not null
   and exists (select 1 from email_verification_codes c where c.user_id = u.id)
   and exists (select 1 from legal_acceptances a where a.user_id = u.id and a.source = 'REGISTRATION');

alter table users
    add constraint ck_users_account_origin check (
        account_origin in ('LEGACY', 'PUBLIC_PASSWORD', 'OAUTH', 'COACH_APPLICATION', 'ADMIN_MANUAL')
    );

create index idx_users_unverified_public_registration_cleanup
    on users (created_at, id)
    where account_origin = 'PUBLIC_PASSWORD' and email_verified = false;
