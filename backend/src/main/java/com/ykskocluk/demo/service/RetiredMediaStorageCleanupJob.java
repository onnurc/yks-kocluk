package com.ykskocluk.demo.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetiredMediaStorageCleanupJob {
    private final RetiredMediaStorageCleanupService cleanup;

    public RetiredMediaStorageCleanupJob(RetiredMediaStorageCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(cron = "${app.media.cleanup.retired-cron:0 */15 * * * *}", zone = "UTC")
    public void run() {
        cleanup.pendingIds().forEach(cleanup::retry);
    }
}
