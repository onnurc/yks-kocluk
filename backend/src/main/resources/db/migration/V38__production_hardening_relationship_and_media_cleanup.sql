-- One student may have at most one live or in-flight paid coach relationship.
-- This is intentionally broader than the existing per-(student, coach) guard so two concurrent
-- checkout requests for different coaches cannot both reserve a relationship.
-- Do not guess which production relationship to keep if legacy data already violates the rule.
-- Fail before any schema change with an actionable diagnostic so operators can reconcile the
-- affected students deliberately, then rerun this single Flyway migration.
do $$
begin
    if exists (
        select 1
          from subscriptions
         where status in ('PENDING_PAYMENT', 'ACTIVE', 'PAST_DUE')
         group by student_user_id
        having count(*) > 1
    ) then
        raise exception using
            errcode = '23505',
            message = 'V38 cannot enforce one live coach relationship: conflicting subscriptions exist',
            hint = 'Reconcile duplicate PENDING_PAYMENT/ACTIVE/PAST_DUE subscriptions per student before rerunning Flyway';
    end if;
end
$$;

create unique index uq_single_live_coach_relationship_per_student
    on subscriptions (student_user_id)
    where status in ('PENDING_PAYMENT', 'ACTIVE', 'PAST_DUE');

-- DELETED is the application-visible terminal state. This timestamp records completion of the
-- separate, retryable object-store cleanup; NULL means the scheduled cleanup must retry.
alter table media_assets
    add column storage_deleted_at timestamp with time zone;

create index idx_media_assets_storage_cleanup
    on media_assets (status, storage_deleted_at, id)
    where status = 'DELETED' and storage_deleted_at is null;
