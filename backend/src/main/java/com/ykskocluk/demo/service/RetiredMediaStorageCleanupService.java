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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RetiredMediaStorageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(RetiredMediaStorageCleanupService.class);

    private final MediaAssetRepository assets;
    private final StorageService storage;
    private final MediaStorageDeletionCompletionService completion;
    private final MediaCleanupProperties properties;

    public RetiredMediaStorageCleanupService(MediaAssetRepository assets, StorageService storage,
                                             MediaStorageDeletionCompletionService completion,
                                             MediaCleanupProperties properties) {
        this.assets = assets;
        this.storage = storage;
        this.completion = completion;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<Long> pendingIds() {
        return assets.findStorageCleanupIds(MediaStatus.DELETED, PageRequest.of(0, properties.batchSize()));
    }

    @Transactional(readOnly = true)
    public void retry(Long assetId) {
        MediaAsset asset = assets.findById(assetId).orElse(null);
        if (asset == null || asset.getStatus() != MediaStatus.DELETED || asset.getStorageDeletedAt() != null) return;
        deleteSafely(asset.getId(), asset.getObjectKey());
    }

    public void deleteSafely(Long assetId, String objectKey) {
        try {
            storage.deleteObject(objectKey);
            completion.markDeleted(assetId);
        } catch (RuntimeException ex) {
            // The DB lifecycle is already terminal. A later scheduled run retries the idempotent
            // object delete; external storage availability never rolls back account deletion.
            log.warn("Retired media storage cleanup will be retried; assetId={}, failureType={}",
                    assetId, ex.getClass().getSimpleName());
        }
    }
}
