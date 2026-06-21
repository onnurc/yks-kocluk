-- V8 — Session Meet link (Phase 4d). Populated after-commit by the notification listener
-- (Meet/mail are external side effects, never inside the booking transaction). Nullable:
-- a freshly booked session has no link until the after-commit listener sets it.

alter table sessions add column meet_link varchar(500);
