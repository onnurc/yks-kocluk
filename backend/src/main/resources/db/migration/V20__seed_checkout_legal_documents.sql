-- Checkout legal documents and transaction linkage for immutable acceptance evidence.

alter table legal_acceptances
    add column subscription_id bigint references subscriptions(id),
    add column payment_id bigint references payments(id),
    add constraint ck_legal_acceptance_checkout_links check (
        (subscription_id is null and payment_id is null)
        or (subscription_id is not null and payment_id is not null)
    );

drop index uq_legal_acceptances_active_evidence;

create unique index uq_legal_acceptances_active_user_evidence
    on legal_acceptances(user_id, legal_document_id, acceptance_type, source)
    where withdrawn_at is null and subscription_id is null and payment_id is null;

create unique index uq_legal_acceptances_active_checkout_evidence
    on legal_acceptances(subscription_id, legal_document_id, acceptance_type, source)
    where withdrawn_at is null and subscription_id is not null and payment_id is not null;

create index idx_legal_acceptances_payment on legal_acceptances(payment_id)
    where payment_id is not null;

-- Placeholder-only content. Approved final wording will be supplied by the legal team.
insert into legal_documents
    (type, document_version, title, content, content_hash, status, required_for_registration,
     published_at, effective_at, created_at, updated_at, version)
values
('PRE_INFORMATION_FORM', '1.0', 'Ön Bilgilendirme Formu (Taslak Yer Tutucu)',
 'PLACEHOLDER: Final Pre-Information Form text will be supplied by the legal team.',
 '0388e1893a70eeafc4b2aca39de01a3520e5ddeaff8af5bebfd29fcfb1c68d9c', 'PUBLISHED', false,
 current_timestamp, current_timestamp, current_timestamp, current_timestamp, 0),
('DISTANCE_SALES_AGREEMENT', '1.0', 'Mesafeli Satış Sözleşmesi (Taslak Yer Tutucu)',
 'PLACEHOLDER: Final Distance Sales Agreement text will be supplied by the legal team.',
 'cb9566350e94f45150fd45fd308a97eb5da969c2d34c92eb81ed45d9c63fca09', 'PUBLISHED', false,
 current_timestamp, current_timestamp, current_timestamp, current_timestamp, 0),
('REFUND_CANCELLATION_POLICY', '1.0', 'İade / İptal Politikası (Taslak Yer Tutucu)',
 'PLACEHOLDER: Final Refund and Cancellation Policy text will be supplied by the legal team.',
 '3d2b0d894dad2a2f5ec2eb8785d0b8a69a52e9fecf8a6c3eab91772ca5ef86a5', 'PUBLISHED', false,
 current_timestamp, current_timestamp, current_timestamp, current_timestamp, 0);
