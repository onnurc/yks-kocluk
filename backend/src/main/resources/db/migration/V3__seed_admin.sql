-- V3 — Bootstrap a single ADMIN user (ADMIN cannot be created via the public API).
--
-- password_hash is a Flyway placeholder. It defaults (see spring.flyway.placeholders
-- in application.yml) to a DEV-ONLY BCrypt hash of the password "admin1234".
-- In real deployments, override it by setting the ADMIN_PASSWORD_HASH env var to a
-- BCrypt hash you generate yourself (SQL cannot hash a plaintext password).
--   Dev login: admin@yks.local / admin1234   <-- change in production!

insert into users (email, password_hash, full_name, role, status, email_verified,
                   created_at, updated_at, version)
values ('admin@yks.local', '${admin_password_hash}', 'Platform Admin', 'ADMIN', 'ACTIVE', true,
        now(), now(), 0);
