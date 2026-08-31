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
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.List;

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
    @Mock MediaPublicTokenGenerator tokenGenerator;
    @Mock AfterCommitStorageDeletionService storageDeletion;
    MediaService service;
    User coach;

    @BeforeEach void setUp() {
        service = new MediaService(assets, users, coaches, students, storage,
                new MediaPolicyProperties(5L * 1024 * 1024, 10L * 1024 * 1024),
                resolver, tokenGenerator, storageDeletion);
        coach = user(7L, Role.COACH);
        lenient().when(users.findById(7L)).thenReturn(Optional.of(coach));
        lenient().when(coaches.existsByUserId(7L)).thenReturn(true);
        lenient().when(tokenGenerator.generate()).thenReturn("a".repeat(64));
        lenient().when(assets.save(any())).thenAnswer(i -> {
            MediaAsset saved = i.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            return saved;
        });
        lenient().when(storage.createPresignedUpload(anyString(), anyString(), anyLong())).thenReturn(
                new StorageService.UploadTarget("https://stub/upload", Instant.now().plusSeconds(600), Map.of("Content-Type", "image/jpeg")));
        lenient().when(storage.readObjectPrefix(anyString(), anyInt()))
                .thenReturn(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
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
        assertThat(captor.getValue().getPublicToken()).isEqualTo("a".repeat(64));
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

    @Test void legacyCoachVideoUploadsAreDisabled() {
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.presign(7L,
                new MediaPresignRequest(MediaType.COACH_INTRO_VIDEO, "video/mp4", 100, "intro.mp4")));
        assertThat(ex.getErrorCode()).isEqualTo("MEDIA_TYPE_NOT_ALLOWED");
        verifyNoInteractions(storage);
    }

    @Test void nonCoachCannotUploadCoachVideo() {
        User student = user(8L, Role.STUDENT); when(users.findById(8L)).thenReturn(Optional.of(student));
        ApiException ex = catchThrowableOfType(ApiException.class, () -> service.presign(8L,
                new MediaPresignRequest(MediaType.COACH_INTRO_VIDEO, "video/mp4", 10, "x.mp4")));
        assertThat(ex.getErrorCode()).isEqualTo("MEDIA_TYPE_NOT_ALLOWED");
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
        verify(storageDeletion).deleteAfterCommit(old.getId(), old.getObjectKey());
        verify(storage, never()).deleteObject(old.getObjectKey());
    }

    @Test void completeSkipsStorageDeleteWhenNoPriorActiveImageExists() {
        MediaAsset fresh = asset(20L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.PENDING_UPLOAD);
        CoachProfile profile = new CoachProfile();
        when(assets.findById(20L)).thenReturn(Optional.of(fresh));
        when(storage.headObject(fresh.getObjectKey())).thenReturn(new StorageService.StoredObjectMetadata(true, "image/jpeg", 100));
        when(coaches.findByUserId(7L)).thenReturn(Optional.of(profile));
        service.complete(7L, 20L);
        assertThat(profile.getProfileImageAsset()).isSameAs(fresh);
        verify(storageDeletion, never()).deleteAfterCommit(any(), anyString());
    }

    @Test void finalizeRejectsContentWhoseBytesDoNotMatchDeclaredMimeType() {
        MediaAsset fresh = asset(20L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.PENDING_UPLOAD);
        when(assets.findById(20L)).thenReturn(Optional.of(fresh));
        when(storage.headObject(fresh.getObjectKey()))
                .thenReturn(new StorageService.StoredObjectMetadata(true, "image/jpeg", 100));
        when(storage.readObjectPrefix(fresh.getObjectKey(), 12))
                .thenReturn("<script>".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        ApiException error = catchThrowableOfType(ApiException.class, () -> service.complete(7L, 20L));

        assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(error.getErrorCode()).isEqualTo("MEDIA_CONTENT_INVALID");
        assertThat(fresh.getStatus()).isEqualTo(MediaStatus.PENDING_UPLOAD);
        verifyNoInteractions(storageDeletion);
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

    @Test void publicAssetDownloadReturnsPresignedUrlNotDirectPublicUrl() {
        MediaAsset profileImage = asset(31L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.ACTIVE);
        when(assets.findById(31L)).thenReturn(Optional.of(profileImage));
        Instant expiry = Instant.now().plusSeconds(600);
        when(storage.createPresignedDownload(profileImage.getObjectKey()))
                .thenReturn(new StorageService.DownloadTarget("https://stub/presigned", expiry));
        User stranger = user(2L, Role.STUDENT);
        when(users.findById(2L)).thenReturn(Optional.of(stranger));

        var response = service.download(2L, 31L);

        assertThat(response.url()).isEqualTo("https://stub/presigned");
        assertThat(response.expiresAt()).isEqualTo(expiry);
        verify(resolver, never()).publicUrl(profileImage);
    }

    @Test void publicPresignedUrlNeedsNoRequesterButRejectsPrivateAssets() {
        MediaAsset image = asset(31L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.ACTIVE);
        when(assets.findByPublicTokenAndStatusAndVisibility(
                image.getPublicToken(), MediaStatus.ACTIVE, MediaVisibility.PUBLIC)).thenReturn(Optional.of(image));
        when(storage.createPresignedDownload(image.getObjectKey()))
                .thenReturn(new StorageService.DownloadTarget("https://stub/presigned", Instant.now().plusSeconds(600)));
        assertThat(service.publicPresignedUrl(image.getPublicToken())).isEqualTo("https://stub/presigned");
        verify(users, never()).findById(anyLong());

        MediaAsset document = asset(32L, coach, MediaType.DOCUMENT, MediaVisibility.PRIVATE, MediaStatus.ACTIVE);
        when(assets.findByPublicTokenAndStatusAndVisibility(
                document.getPublicToken(), MediaStatus.ACTIVE, MediaVisibility.PUBLIC)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.publicPresignedUrl(document.getPublicToken())).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo("MEDIA_NOT_FOUND");
        verify(storage, never()).createPresignedDownload(document.getObjectKey());
    }

    @Test void numericDatabaseIdsAndInactiveTokensCannotResolvePublicMedia() {
        assertThatThrownBy(() -> service.publicPresignedUrl("31")).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getErrorCode()).isEqualTo("MEDIA_NOT_FOUND");

        String deletedToken = "d".repeat(64);
        when(assets.findByPublicTokenAndStatusAndVisibility(
                deletedToken, MediaStatus.ACTIVE, MediaVisibility.PUBLIC)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.publicPresignedUrl(deletedToken)).isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getErrorCode()).isEqualTo("MEDIA_NOT_FOUND");
        verify(storage, never()).createPresignedDownload(anyString());
    }

    @Test void cannotDeleteAnotherUsersAsset() {
        MediaAsset asset = asset(40L, coach, MediaType.DOCUMENT, MediaVisibility.PRIVATE, MediaStatus.ACTIVE);
        when(assets.findById(40L)).thenReturn(Optional.of(asset));
        assertThatThrownBy(() -> service.delete(2L, 40L)).isInstanceOf(ApiException.class);
        verify(storageDeletion, never()).deleteAfterCommit(any(), anyString());
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.ACTIVE);
    }

    @Test void ownerDeleteRemovesObjectAndMarksLifecycleDeleted() {
        MediaAsset asset = asset(41L, coach, MediaType.DOCUMENT, MediaVisibility.PRIVATE, MediaStatus.ACTIVE);
        when(assets.findById(41L)).thenReturn(Optional.of(asset));
        when(coaches.findByUserId(7L)).thenReturn(Optional.empty());
        when(students.findByUserId(7L)).thenReturn(Optional.empty());
        service.delete(7L, 41L);
        verify(storageDeletion).deleteAfterCommit(asset.getId(), asset.getObjectKey());
        verify(storage, never()).deleteObject(asset.getObjectKey());
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.DELETED);
    }

    @Test void ownerDeleteDetachesCoachProfileImage() {
        MediaAsset asset = asset(42L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.ACTIVE);
        CoachProfile profile = new CoachProfile();
        profile.setProfileImageAsset(asset);
        when(assets.findById(42L)).thenReturn(Optional.of(asset));
        when(coaches.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(students.findByUserId(7L)).thenReturn(Optional.empty());

        service.delete(7L, 42L);

        verify(storageDeletion).deleteAfterCommit(asset.getId(), asset.getObjectKey());
        assertThat(profile.getProfileImageAsset()).isNull();
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.DELETED);
    }

    @Test void completingProfileImageWithoutProfileReturnsMeaningfulConflict() {
        MediaAsset fresh = asset(50L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.PENDING_UPLOAD);
        when(assets.findById(50L)).thenReturn(Optional.of(fresh));
        when(storage.headObject(fresh.getObjectKey())).thenReturn(new StorageService.StoredObjectMetadata(true, "image/jpeg", 100));
        when(coaches.findByUserId(7L)).thenReturn(Optional.empty());

        ApiException error = catchThrowableOfType(ApiException.class, () -> service.complete(7L, 50L));

        assertThat(error.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
        assertThat(error.getErrorCode()).isEqualTo("MEDIA_PROFILE_MISSING");
    }

    @Test void accountDeletionRetiresPublicAndPrivateMediaAndDetachesProfileReferences() {
        MediaAsset image = asset(51L, coach, MediaType.PROFILE_IMAGE, MediaVisibility.PUBLIC, MediaStatus.ACTIVE);
        MediaAsset document = asset(52L, coach, MediaType.DOCUMENT, MediaVisibility.PRIVATE, MediaStatus.ACTIVE);
        CoachProfile profile = new CoachProfile();
        profile.setProfileImageAsset(image);
        when(assets.findByOwnerIdAndStorageDeletedAtIsNullOrderByIdAsc(7L)).thenReturn(List.of(image, document));
        when(coaches.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(students.findByUserId(7L)).thenReturn(Optional.empty());

        assertThat(service.retireAllOwnedBy(7L)).isEqualTo(2);

        assertThat(image.getStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(document.getStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(profile.getProfileImageAsset()).isNull();
        verify(storageDeletion).deleteAfterCommit(51L, image.getObjectKey());
        verify(storageDeletion).deleteAfterCommit(52L, document.getObjectKey());
        verify(storage, never()).deleteObject(anyString());
    }

    private User user(Long id, Role role) { User u = new User(); ReflectionTestUtils.setField(u, "id", id); u.setRole(role); return u; }
    private MediaAsset asset(Long id, User owner, MediaType type, MediaVisibility visibility, MediaStatus status) {
        MediaAsset a = new MediaAsset(); ReflectionTestUtils.setField(a, "id", id); a.setOwner(owner);
        a.setPublicToken(String.format("%064x", id));
        a.setObjectKey((visibility == MediaVisibility.PUBLIC ? "public/" : "private/") + id + ".jpg");
        a.setContentType("image/jpeg"); a.setSizeBytes(100); a.setMediaType(type); a.setVisibility(visibility); a.setStatus(status); return a;
    }
}
