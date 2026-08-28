package com.ykskocluk.demo.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Refuses to start a non-local deployment with an unsafe admin bootstrap value or with the
 * historical V3 default still stored in an already-migrated database.
 *
 * <p>Changing {@code ADMIN_PASSWORD_HASH} cannot update a migration that has already run. The
 * database check is therefore intentionally independent from the configured bootstrap hash and
 * fails with a rotation instruction instead of silently overwriting a password changed by an
 * administrator.</p>
 */
@Component
public class AdminBootstrapSecurityValidator implements SmartInitializingSingleton {

    static final String BOOTSTRAP_EMAIL = "admin@yks.local";
    static final String HISTORICAL_DEFAULT_HASH =
            "$2y$10$yl6BvY2.9REyRTbV.9FYP.2xPDn2C1ZhDaggZQLcYAyRBc9Ri4.HC";
    private static final Pattern BCRYPT =
            Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;

    public AdminBootstrapSecurityValidator(Environment environment, JdbcTemplate jdbcTemplate) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    void validate() {
        if (environment.acceptsProfiles(Profiles.of("local", "test", "stub"))) {
            return;
        }

        String configuredHash = environment.getProperty("ADMIN_PASSWORD_HASH");
        if (!isSecureBcrypt(configuredHash) || HISTORICAL_DEFAULT_HASH.equals(configuredHash)) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD_HASH must be an explicit non-default BCrypt hash outside local/test/stub");
        }

        List<String> storedHashes = jdbcTemplate.query(
                "select password_hash from users where lower(email) = lower(?) and role = 'ADMIN'",
                (rs, rowNum) -> rs.getString(1), BOOTSTRAP_EMAIL);
        for (String storedHash : storedHashes) {
            if (!isSecureBcrypt(storedHash) || HISTORICAL_DEFAULT_HASH.equals(storedHash)) {
                throw new IllegalStateException(
                        "The bootstrap admin credential in the database must be rotated before non-local startup");
            }
        }
    }

    private boolean isSecureBcrypt(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        if (!BCRYPT.matcher(normalized).matches()) {
            return false;
        }
        int cost = Integer.parseInt(normalized.substring(4, 6));
        return cost >= 10 && cost <= 16;
    }
}
