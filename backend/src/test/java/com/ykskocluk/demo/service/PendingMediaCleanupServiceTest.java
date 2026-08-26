package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MediaCleanupProperties;
import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.repository.MediaAssetRepository;
import com.ykskocluk.demo.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingMediaCleanupServiceTest {
    @Mock MediaAssetRepository assets;
    @Mock StorageService storage;
    PendingMediaCleanupService service;

    @BeforeEach
    void setUp() {
        service = new PendingMediaCleanupService(assets, storage,
                new MediaCleanupProperties(Duration.ofHours(24), 50));
    }

    @Test
    void staleLookupIsStatusScopedAndBounded() {
        Instant cutoff = Instant.parse("2026-08-25T10:00:00Z");
        when(assets.findStaleIds(eq(MediaStatus.PENDING_UPLOAD), eq(cutoff), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));

        assertThat(service.stalePendingIds(cutoff)).containsExactly(1L, 2L);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(assets).findStaleIds(eq(MediaStatus.PENDING_UPLOAD), eq(cutoff), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void deletesOnlyStalePendingAssetAndMarksItDeleted() {
        Instant cutoff = Instant.parse("2026-08-25T10:00:00Z");
        MediaAsset pending = asset(MediaStatus.PENDING_UPLOAD, cutoff.minusSeconds(1));
        when(assets.findById(1L)).thenReturn(Optional.of(pending));

        assertThat(service.cleanupOne(1L, cutoff)).isTrue();
        verify(storage).deleteObject("private/pending.pdf");
        assertThat(pending.getStatus()).isEqualTo(MediaStatus.DELETED);

        MediaAsset active = asset(MediaStatus.ACTIVE, cutoff.minusSeconds(3600));
        when(assets.findById(2L)).thenReturn(Optional.of(active));
        assertThat(service.cleanupOne(2L, cutoff)).isFalse();
        assertThat(active.getStatus()).isEqualTo(MediaStatus.ACTIVE);
    }

    @Test
    void storageFailureLeavesPendingStateForRetry() {
        Instant cutoff = Instant.parse("2026-08-25T10:00:00Z");
        MediaAsset pending = asset(MediaStatus.PENDING_UPLOAD, cutoff.minusSeconds(1));
        when(assets.findById(1L)).thenReturn(Optional.of(pending));
        doThrow(new IllegalStateException("R2 unavailable")).when(storage).deleteObject(pending.getObjectKey());

        assertThat(service.cleanupOne(1L, cutoff)).isFalse();
        assertThat(pending.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
    }

    @Test
    void freshPendingAssetIsUntouched() {
        Instant cutoff = Instant.parse("2026-08-25T10:00:00Z");
        MediaAsset pending = asset(MediaStatus.PENDING_UPLOAD, cutoff.plusSeconds(1));
        when(assets.findById(1L)).thenReturn(Optional.of(pending));

        assertThat(service.cleanupOne(1L, cutoff)).isFalse();
        verify(storage, never()).deleteObject(any());
    }

    private MediaAsset asset(MediaStatus status, Instant createdAt) {
        MediaAsset asset = new MediaAsset();
        asset.setObjectKey("private/pending.pdf");
        asset.setStatus(status);
        ReflectionTestUtils.setField(asset, "createdAt", createdAt);
        return asset;
    }
}
