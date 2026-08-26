package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MediaPublicUrlProperties;
import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaVisibility;
import org.springframework.stereotype.Component;
import org.mapstruct.Named;

@Component
public class MediaAssetUrlResolver {
    private final String publicBaseUrl;

    public MediaAssetUrlResolver(MediaPublicUrlProperties properties) {
        this.publicBaseUrl = properties.publicBaseUrl();
    }

    /**
     * Points at this backend's own redirect endpoint rather than the bucket's public hostname:
     * the endpoint signs a short-lived storage URL per request. That keeps the object key and the
     * bucket host out of clients, and means the browser only ever has to reach this API.
     */
    @Named("publicMediaUrl")
    public String publicUrl(MediaAsset asset) {
        if (asset == null || asset.getPublicToken() == null || publicBaseUrl == null) return null;
        if (asset.getStatus() != MediaStatus.ACTIVE || asset.getVisibility() != MediaVisibility.PUBLIC) return null;
        return publicBaseUrl + "/api/v1/public/media/" + asset.getPublicToken();
    }

    @Named("activeAssetId")
    public Long activeAssetId(MediaAsset asset) {
        if (asset == null || asset.getStatus() != MediaStatus.ACTIVE) return null;
        return asset.getId();
    }
}
