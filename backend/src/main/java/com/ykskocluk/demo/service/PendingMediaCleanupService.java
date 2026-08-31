package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MediaCleanupProperties;
import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.repository.MediaAssetRepository;
import com.ykskocluk.demo.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PendingMediaCleanupService {
    private static final Logger log = LoggerFactory.getLogger(PendingMediaCleanupService.class);

    private final MediaAssetRepository assets;
    private final StorageService storage;
    private final MediaCleanupProperties properties;

    public PendingMediaCleanupService(MediaAssetRepository assets, StorageService storage,
                                      MediaCleanupProperties properties) {
        this.assets = assets;
        this.storage = storage;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<Long> stalePendingIds(Instant cutoff) {
        return assets.findStaleIds(MediaStatus.PENDING_UPLOAD, cutoff,
                PageRequest.of(0, properties.batchSize()));
    }

    /**
     * PENDING objects are never user-visible or profile-referenced. Delete is attempted first so a
     * storage failure leaves the row PENDING for a later retry; object-store delete is idempotent if
     * the DB commit itself fails and the next scheduled run sees the row again.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cleanupOne(Long assetId, Instant cutoff) {
        MediaAsset asset = assets.findById(assetId).orElse(null);
        if (asset == null || asset.getStatus() != MediaStatus.PENDING_UPLOAD
                || asset.getCreatedAt() == null || !asset.getCreatedAt().isBefore(cutoff)) {
            return false;
        }
        try {
            storage.deleteObject(asset.getObjectKey());
        } catch (RuntimeException ex) {
            log.warn("Stale pending media storage cleanup failed; assetId={}", assetId, ex);
            return false;
        }
        asset.setStatus(MediaStatus.DELETED);
        asset.setStorageDeletedAt(Instant.now());
        return true;
    }
}
