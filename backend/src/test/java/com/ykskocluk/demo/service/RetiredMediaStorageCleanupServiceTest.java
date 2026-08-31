package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MediaCleanupProperties;
import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.repository.MediaAssetRepository;
import com.ykskocluk.demo.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetiredMediaStorageCleanupServiceTest {
    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final MediaStorageDeletionCompletionService completion = mock(MediaStorageDeletionCompletionService.class);
    private final RetiredMediaStorageCleanupService service = new RetiredMediaStorageCleanupService(
            assets, storage, completion, new MediaCleanupProperties(Duration.ofHours(24), 100));

    @Test
    void storageFailureIsContainedSoCommittedAccountDeletionCanBeRetried() {
        doThrow(new IllegalStateException("R2 unavailable")).when(storage).deleteObject("private/document.pdf");

        assertThatCode(() -> service.deleteSafely(71L, "private/document.pdf")).doesNotThrowAnyException();

        verifyNoInteractions(completion);
    }

    @Test
    void retryDeletesOnlyTerminalMediaAndMarksPhysicalCompletion() {
        MediaAsset asset = new MediaAsset();
        asset.setStatus(MediaStatus.DELETED);
        asset.setObjectKey("public/profile.jpg");
        org.springframework.test.util.ReflectionTestUtils.setField(asset, "id", 72L);
        when(assets.findById(72L)).thenReturn(Optional.of(asset));

        service.retry(72L);

        verify(storage).deleteObject("public/profile.jpg");
        verify(completion).markDeleted(72L);
    }

    @Test
    void repeatedRetryAfterCompletionIsIdempotentlySkipped() {
        MediaAsset asset = new MediaAsset();
        asset.setStatus(MediaStatus.DELETED);
        asset.setStorageDeletedAt(java.time.Instant.now());
        when(assets.findById(73L)).thenReturn(Optional.of(asset));

        service.retry(73L);

        verifyNoInteractions(storage, completion);
    }
}
