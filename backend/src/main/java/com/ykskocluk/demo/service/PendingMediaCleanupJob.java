package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MediaCleanupProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class PendingMediaCleanupJob {
    private final PendingMediaCleanupService cleanupService;
    private final MediaCleanupProperties properties;
    private final Clock clock;

    public PendingMediaCleanupJob(PendingMediaCleanupService cleanupService,
                                  MediaCleanupProperties properties, Clock clock) {
        this.cleanupService = cleanupService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.media.cleanup.cron:0 10 * * * *}", zone = "UTC")
    public void cleanup() {
        Instant cutoff = clock.instant().minus(properties.pendingUploadExpiration());
        for (Long assetId : cleanupService.stalePendingIds(cutoff)) {
            cleanupService.cleanupOne(assetId, cutoff);
        }
    }
}
