-- V13 — Admin subscription termination (Phase 9)

alter table subscriptions add column termination_reason varchar(2000);
