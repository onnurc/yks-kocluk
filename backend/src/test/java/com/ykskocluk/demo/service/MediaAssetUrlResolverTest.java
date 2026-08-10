package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaVisibility;
import com.ykskocluk.demo.storage.StorageService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MediaAssetUrlResolverTest {
    private final StorageService storage = mock(StorageService.class);
    private final MediaAssetUrlResolver resolver = new MediaAssetUrlResolver(storage);

    @Test void resolvesOnlyActivePublicAssets() {
        MediaAsset asset = new MediaAsset(); asset.setObjectKey("public/image.jpg");
        asset.setVisibility(MediaVisibility.PUBLIC); asset.setStatus(MediaStatus.ACTIVE);
        when(storage.resolvePublicUrl(asset.getObjectKey())).thenReturn("https://media.example.com/public/image.jpg");
        assertThat(resolver.publicUrl(asset)).isEqualTo("https://media.example.com/public/image.jpg");

        asset.setStatus(MediaStatus.PENDING_UPLOAD);
        assertThat(resolver.publicUrl(asset)).isNull();
        asset.setStatus(MediaStatus.ACTIVE); asset.setVisibility(MediaVisibility.PRIVATE);
        assertThat(resolver.publicUrl(asset)).isNull();
        asset.setVisibility(MediaVisibility.PUBLIC); asset.setStatus(MediaStatus.DELETED);
        assertThat(resolver.publicUrl(asset)).isNull();
        verify(storage, times(1)).resolvePublicUrl(anyString());
    }
}
