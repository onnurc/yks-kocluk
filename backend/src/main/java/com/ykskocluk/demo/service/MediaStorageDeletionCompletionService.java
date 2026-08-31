package com.ykskocluk.demo.service;

import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.repository.MediaAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MediaStorageDeletionCompletionService {
    private final MediaAssetRepository assets;

    public MediaStorageDeletionCompletionService(MediaAssetRepository assets) {
        this.assets = assets;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeleted(Long assetId) {
        assets.findById(assetId).filter(asset -> asset.getStatus() == MediaStatus.DELETED).ifPresent(asset -> {
            if (asset.getStorageDeletedAt() == null) asset.setStorageDeletedAt(Instant.now());
        });
    }
}
