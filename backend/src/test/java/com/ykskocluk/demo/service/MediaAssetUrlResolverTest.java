package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MediaAssetUrlResolverTest {
    private final MediaAssetUrlResolver resolver = new MediaAssetUrlResolver("https://api.example.com");

    @Test void resolvesOnlyActivePublicAssets() {
        MediaAsset asset = activePublicAsset();
        assertThat(resolver.publicUrl(asset)).isEqualTo("https://api.example.com/api/v1/public/media/42");

        asset.setStatus(MediaStatus.PENDING_UPLOAD);
        assertThat(resolver.publicUrl(asset)).isNull();
        asset.setStatus(MediaStatus.ACTIVE); asset.setVisibility(MediaVisibility.PRIVATE);
        assertThat(resolver.publicUrl(asset)).isNull();
        asset.setVisibility(MediaVisibility.PUBLIC); asset.setStatus(MediaStatus.DELETED);
        assertThat(resolver.publicUrl(asset)).isNull();
    }

    @Test void trailingSlashInBaseUrlDoesNotDoubleUp() {
        assertThat(new MediaAssetUrlResolver("https://api.example.com/").publicUrl(activePublicAsset()))
                .isEqualTo("https://api.example.com/api/v1/public/media/42");
    }

    @Test void unsavedAssetHasNoPublicUrl() {
        MediaAsset asset = activePublicAsset();
        ReflectionTestUtils.setField(asset, "id", null);
        assertThat(resolver.publicUrl(asset)).isNull();
    }

    private MediaAsset activePublicAsset() {
        MediaAsset asset = new MediaAsset();
        ReflectionTestUtils.setField(asset, "id", 42L);
        asset.setObjectKey("public/image.jpg");
        asset.setVisibility(MediaVisibility.PUBLIC);
        asset.setStatus(MediaStatus.ACTIVE);
        return asset;
    }
}
