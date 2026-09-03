-- Makes the singleton YKS configuration explicit and snapshots the exact exam used by a purchase.
alter table package_settings add column yks_exam_year integer;
alter table package_settings add column active boolean not null default false;

update package_settings
set yks_exam_year = extract(year from yks_exam_date)::integer,
    active = yks_exam_date is not null;

alter table package_settings add constraint ck_package_settings_exam_values check (
    (yks_exam_year is null and yks_exam_date is null)
    or (yks_exam_year is not null and yks_exam_date is not null
        and yks_exam_year = extract(year from yks_exam_date)::integer)
);
alter table package_settings add constraint ck_package_settings_active_exam check
    (not active or yks_exam_date is not null);

alter table subscriptions add column yks_exam_year_snapshot integer;
alter table subscriptions add column yks_exam_date_snapshot date;
alter table subscriptions add constraint ck_subscription_exam_snapshot check (
    (yks_exam_year_snapshot is null and yks_exam_date_snapshot is null)
    or (yks_exam_year_snapshot is not null and yks_exam_date_snapshot is not null
        and yks_exam_year_snapshot = extract(year from yks_exam_date_snapshot)::integer)
);
