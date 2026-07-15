package com.ykskocluk.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring scheduling (Phase 8c). {@code @Profile("!test")} so the scheduler never auto-fires
 * during tests — the renewal job is invoked directly there, avoiding cron interference/flakiness
 * (same discipline as {@code ResendConfig}). Single-instance assumption for beta; the
 * {@code UNIQUE(idempotency_key)} guard makes overlapping/multi-instance runs safe regardless.
 */
@Configuration
@Profile("!test")
@EnableScheduling
public class SchedulingConfig {
}
