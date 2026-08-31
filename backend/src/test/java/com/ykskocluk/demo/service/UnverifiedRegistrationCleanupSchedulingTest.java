package com.ykskocluk.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

class UnverifiedRegistrationCleanupSchedulingTest {

    @Test
    void cleanupIsScheduledHourlyInUtcWithConfigurableCron() throws Exception {
        Scheduled scheduled = UnverifiedRegistrationCleanupService.class
                .getMethod("cleanup")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron())
                .isEqualTo("${app.email-verification.unverified-account-cleanup-cron:0 0 * * * *}");
        assertThat(scheduled.zone()).isEqualTo("UTC");
    }
}
