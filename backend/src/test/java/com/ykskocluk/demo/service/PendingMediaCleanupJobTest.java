package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MediaCleanupProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingMediaCleanupJobTest {
    @Mock PendingMediaCleanupService cleanupService;

    @Test
    void scheduledRunUsesConfiguredAgeAndProcessesOnlyBoundedCandidateIds() {
        Instant now = Instant.parse("2026-08-26T10:00:00Z");
        Instant cutoff = now.minus(Duration.ofHours(24));
        MediaCleanupProperties properties = new MediaCleanupProperties(Duration.ofHours(24), 100);
        when(cleanupService.stalePendingIds(cutoff)).thenReturn(List.of(1L, 2L));
        PendingMediaCleanupJob job = new PendingMediaCleanupJob(
                cleanupService, properties, Clock.fixed(now, ZoneOffset.UTC));

        job.cleanup();

        verify(cleanupService).cleanupOne(1L, cutoff);
        verify(cleanupService).cleanupOne(2L, cutoff);
    }
}
