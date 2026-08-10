package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaVisibility;
import com.ykskocluk.demo.storage.StorageService;
import org.springframework.stereotype.Component;
import org.mapstruct.Named;

@Component
public class MediaAssetUrlResolver {
    private final StorageService storageService;

    public MediaAssetUrlResolver(StorageService storageService) { this.storageService = storageService; }

    @Named("publicMediaUrl")
    public String publicUrl(MediaAsset asset) {
        if (asset == null || asset.getStatus() != MediaStatus.ACTIVE || asset.getVisibility() != MediaVisibility.PUBLIC) return null;
        return storageService.resolvePublicUrl(asset.getObjectKey());
    }
}
