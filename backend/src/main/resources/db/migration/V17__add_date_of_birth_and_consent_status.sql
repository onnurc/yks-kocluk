-- Flyway migration V17: Add date_of_birth to users and status to consent_records
ALTER TABLE users ADD COLUMN date_of_birth DATE;

-- 1. status kolonunu nullable olarak ekle
ALTER TABLE consent_records ADD COLUMN status VARCHAR(20);

-- 2. Eski consent kayıtlarını ACCEPTED olarak backfill et
UPDATE consent_records SET status = 'ACCEPTED';

-- 3. status kolonunu NOT NULL yap
ALTER TABLE consent_records ALTER COLUMN status SET NOT NULL;

-- 4. Gelecekteki yeni kayıtlar için güvenli default PENDING kullan
ALTER TABLE consent_records ALTER COLUMN status SET DEFAULT 'PENDING';
