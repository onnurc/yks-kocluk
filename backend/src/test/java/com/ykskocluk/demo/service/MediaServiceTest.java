package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MediaPolicyProperties;
import com.ykskocluk.demo.dto.MediaPresignRequest;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import com.ykskocluk.demo.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {
    @Mock MediaAssetRepository assets;
    @Mock UserRepository users;
    @Mock CoachProfileRepository coaches;
    @Mock StudentProfileRepository students;
    @Mock StorageService storage;
    @Mock MediaAssetUrlResolver resolver;
    MediaService service;
    User coach;

    @BeforeEach void setUp() {
        service = new MediaService(assets, users, coaches, students, storage,
                new MediaPolicyProperties(5L * 1024 * 1024, 20L * 1024 * 1024, 10L * 1024 * 1024), resolver);
        coach = user(7L, Role.COACH);
        lenient().when(users.findById(7L)).thenReturn(Optional.of(coach));
        lenient().when(coaches.existsByUserId(7L)).thenReturn(true);
        lenient().when(assets.save(any())).thenAnswer(i -> {
            MediaAsset saved = i.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
        lenient().when(storage.createPresignedUpload(anyString(), anyString(), anyLong())).thenReturn(
                new StorageService.UploadTarget("https://stub/upload", Instant.now().plusSeconds(600), Map.of("Content-Type", "image/jpeg")));
    }

    @Test void validProfileImagePresignGeneratesServerKeyWithoutPii() {
        coach.setEmail("secret@example.com"); coach.setFullName("Private Name");
        var result = service.presign(7L, new MediaPresignRequest(MediaType.PROFILE_IMAGE, "image/jpeg", 100, "../../photo.jpg"));
        assertThat(result.assetId()).isEqualTo(99L);
        assertThat(result.objectKey()).startsWith("public/profile-images/7/").endsWith(".jpg")
                .doesNotContain("secret", "Private", "..", "photo.jpg");
        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
        verify(assets).save(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo(MediaVisibility.PUBLIC);
        assertThat(captor.getValue().getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
    }

    @Test void unsupportedMimeIsRejected() {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.presign(7L,
                new MediaPresignRequest(MediaType.PROFILE_IMAGE, "image/gif", 100, "x.gif")));
        assertThat(ex.getErrorCode()).isEqualTo("MEDIA_TYPE_NOT_ALLOWED");
        verifyNoInteractions(storage);
    }

    @Test void oversizedProfileImageIsRejected() {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.presign(7L,
                new MediaPresignRequest(MediaType.PROFILE_IMAGE, "image/png", 5L * 1024 * 1024 + 1, "x.png")));
        assertThat(ex.getErrorCode()).isEqualTo("MEDIA_SIZE_EXCEEDED");
    }

    @Test void coachVideoBoundaryAcceptedButOversizeRejected() {
        assertThatCode(() -> service.presign(7L, new MediaPresignRequest(MediaType.COACH_INTRO_VIDEO,
                "video/mp4", 20L * 1024 * 1024, "intro.mp4"))).doesNotThrowAnyException();
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.presign(7L,
                new MediaPresignRequest(MediaType.COACH_INTRO_VIDEO, "video/mp4", 20L * 1024 * 1024 + 1, "intro.mp4")));
        assertThat(ex.getErrorCode()).isEqualTo("MEDIA_SIZE_EXCEEDED");
    }

    @Test void nonCoachCannotUploadCoachVideo() {
        User student = user(8L, Role.STUDENT); when(users.findById(8L)).thenReturn(Optional.of(student));
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.presign(8L,
                new MediaPresignRequest(MediaType.COACH_INTRO_VIDEO, "video/mp4", 10, "x.mp4")));
        assertThat(ex.getErrorCode()).isEqualTo("MEDIA_OWNER_INVALID");
    }

    @Test void documentVisibilityIsAlwaysPrivate() {
        service.presign(7L, new MediaPresignRequest(MediaType.DOCUMENT, "application/pdf", 100, "diploma.pdf"));
        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class); verify(assets).save(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
    }

    @Test void cannotFinalizeAnotherUsersUpload() {
        MediaAsset asset = asset(20L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.PENDING_UPLOAD);
        when(assets.findById(20L)).thenReturn(Optional.of(asset));
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.complete(8L, 20L));
        assertThat(ex.getErrorCode()).isEqualTo("MEDIA_OWNER_INVALID");
        verify(storage, never()).headObject(anyString());
    }

    @Test void nonexistentUploadCannotFinalize() {
        MediaAsset asset = asset(20L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.PENDING_UPLOAD);
        when(assets.findById(20L)).thenReturn(Optional.of(asset));
        when(storage.headObject(asset.getObjectKey())).thenReturn(new StorageService.StoredObjectMetadata(false, null, 0));
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.complete(7L, 20L));
        assertThat(ex.getErrorCode()).isEqualTo("MEDIA_UPLOAD_NOT_FOUND");
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
    }

    @Test void completeActivatesAndReplacesProfileImageAndDeletesOldFromStorage() {
        MediaAsset old = asset(19L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.ACTIVE);
        MediaAsset fresh = asset(20L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.PENDING_UPLOAD);
        CoachProfile profile = new CoachProfile(); profile.setProfileImageAsset(old);
        when(assets.findById(20L)).thenReturn(Optional.of(fresh));
        when(storage.headObject(fresh.getObjectKey())).thenReturn(new StorageService.StoredObjectMetadata(true, "image/jpeg", 100));
        when(coaches.findByUserId(7L)).thenReturn(Optional.of(profile));
        service.complete(7L, 20L);
        assertThat(fresh.getStatus()).isEqualTo(MediaStatus.ACTIVE);
        assertThat(old.getStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(profile.getProfileImageAsset()).isSameAs(fresh);
        verify(storage).deleteObject(old.getObjectKey());
    }

    @Test void completeSkipsStorageDeleteWhenNoPriorActiveImageExists() {
        MediaAsset fresh = asset(20L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.PENDING_UPLOAD);
        CoachProfile profile = new CoachProfile();
        when(assets.findById(20L)).thenReturn(Optional.of(fresh));
        when(storage.headObject(fresh.getObjectKey())).thenReturn(new StorageService.StoredObjectMetadata(true, "image/jpeg", 100));
        when(coaches.findByUserId(7L)).thenReturn(Optional.of(profile));
        service.complete(7L, 20L);
        assertThat(profile.getProfileImageAsset()).isSameAs(fresh);
        verify(storage, never()).deleteObject(anyString());
    }

    @Test void privateDocumentDownloadOwnerAndAdminAllowedUnrelatedDenied() {
        MediaAsset document = asset(30L, coach, MediaType.DOCUMENT, MediaVisibility.PRIVATE, MediaStatus.ACTIVE);
        when(assets.findById(30L)).thenReturn(Optional.of(document));
        when(storage.createPresignedDownload(document.getObjectKey())).thenReturn(new StorageService.DownloadTarget("https://stub/download", Instant.now()));
        assertThat(service.download(7L, 30L).url()).contains("stub");
        User admin = user(1L, Role.ADMIN); when(users.findById(1L)).thenReturn(Optional.of(admin));
        assertThat(service.download(1L, 30L).url()).contains("stub");
        User stranger = user(2L, Role.STUDENT); when(users.findById(2L)).thenReturn(Optional.of(stranger));
        assertThatThrownBy(() -> service.download(2L, 30L)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException)e).getErrorCode()).isEqualTo("MEDIA_OWNER_INVALID");
        verify(resolver, never()).publicUrl(document);
    }

    @Test void cannotDeleteAnotherUsersAsset() {
        MediaAsset asset = asset(40L, coach, MediaType.DOCUMENT, MediaVisibility.PRIVATE, MediaStatus.ACTIVE);
        when(assets.findById(40L)).thenReturn(Optional.of(asset));
        assertThatThrownBy(() -> service.delete(2L, 40L)).isInstanceOf(ApiException.class);
        verify(storage, never()).deleteObject(anyString());
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.ACTIVE);
    }

    @Test void ownerDeleteRemovesObjectAndMarksLifecycleDeleted() {
        MediaAsset asset = asset(41L, coach, MediaType.DOCUMENT, MediaVisibility.PRIVATE, MediaStatus.ACTIVE);
        when(assets.findById(41L)).thenReturn(Optional.of(asset));
        when(coaches.findByUserId(7L)).thenReturn(Optional.empty());
        when(students.findByUserId(7L)).thenReturn(Optional.empty());
        service.delete(7L, 41L);
        verify(storage).deleteObject(asset.getObjectKey());
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.DELETED);
    }

    private User user(Long id, Role role) { User u = new User(); ReflectionTestUtils.setField(u, "id", id); u.setRole(role); return u; }
    private MediaAsset asset(Long id, User owner, MediaType type, MediaVisibility visibility, MediaStatus status) {
        MediaAsset a = new MediaAsset(); ReflectionTestUtils.setField(a, "id", id); a.setOwner(owner);
        a.setObjectKey((visibility == MediaVisibility.PUBLIC ? "public/" : "private/") + id + ".jpg");
        a.setContentType("image/jpeg"); a.setSizeBytes(100); a.setMediaType(type); a.setVisibility(visibility); a.setStatus(status); return a;
    }
}
