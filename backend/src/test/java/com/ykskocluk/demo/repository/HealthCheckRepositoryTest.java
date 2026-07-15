package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.TestcontainersConfiguration;
import com.ykskocluk.demo.entity.HealthCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository slice test against a real PostgreSQL container (Testcontainers).
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} stops Spring from swapping in
 * H2 (forbidden by CLAUDE.md). Flyway runs V1__baseline, which seeds one health_check
 * row — this test verifies the repository can read it back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HealthCheckRepositoryTest {

    @Autowired
    HealthCheckRepository healthCheckRepository;

    @Test
    void readsBaselineSeedRow() {
        List<HealthCheck> rows = healthCheckRepository.findAll();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo("UP");
    }
}
