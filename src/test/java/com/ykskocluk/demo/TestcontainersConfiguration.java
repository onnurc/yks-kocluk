package com.ykskocluk.demo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared Testcontainers wiring for DB-touching tests. The real PostgreSQL container
 * is exposed to Spring via {@link ServiceConnection}, so no datasource URL is needed
 * in application-test.yml. CLAUDE.md forbids H2 — all DB tests use real PostgreSQL.
 *
 * <p>NOTE: Testcontainers 2.0 relocated this class to {@code org.testcontainers.postgresql}
 * (was {@code org.testcontainers.containers} in 1.x) and it is now non-generic.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Match Neon's PostgreSQL major version (18) so tests run against the same engine.
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
