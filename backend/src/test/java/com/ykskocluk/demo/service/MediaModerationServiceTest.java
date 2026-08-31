package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.entity.MediaModerationLog;
import com.ykskocluk.demo.entity.User;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaType;
import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.CoachProfileRepository;
import com.ykskocluk.demo.repository.MediaAssetRepository;
import com.ykskocluk.demo.repository.MediaModerationLogRepository;
import com.ykskocluk.demo.repository.StudentProfileRepository;
import com.ykskocluk.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaModerationServiceTest {
    @Mock MediaAssetRepository assets;
    @Mock MediaModerationLogRepository logs;
    @Mock UserRepository users;
    @Mock CoachProfileRepository coaches;
    @Mock StudentProfileRepository students;
    @Mock AfterCommitStorageDeletionService storageDeletion;
    MediaModerationService service;
    Instant now = Instant.parse("2026-08-26T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new MediaModerationService(assets, logs, users, coaches, students,
                storageDeletion, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void adminRemovalDetachesImageSchedulesDeletionAndPersistsAudit() {
        User admin = user(1L, Role.ADMIN);
        User owner = user(7L, Role.COACH);
        MediaAsset asset = profileImage(42L, owner);
        CoachProfile profile = new CoachProfile();
        profile.setProfileImageAsset(asset);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(assets.findById(42L)).thenReturn(Optional.of(asset));
        when(coaches.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(students.findByUserId(7L)).thenReturn(Optional.empty());

        service.removeProfileImage(1L, 42L, "  Uygunsuz profil görseli  ");

        assertThat(profile.getProfileImageAsset()).isNull();
        assertThat(asset.getStatus()).isEqualTo(MediaStatus.DELETED);
        verify(storageDeletion).deleteAfterCommit(asset.getId(), asset.getObjectKey());
        ArgumentCaptor<MediaModerationLog> captor = ArgumentCaptor.forClass(MediaModerationLog.class);
        verify(logs).save(captor.capture());
        assertThat(captor.getValue().getAdmin()).isSameAs(admin);
        assertThat(captor.getValue().getAsset()).isSameAs(asset);
        assertThat(captor.getValue().getTargetOwner()).isSameAs(owner);
        assertThat(captor.getValue().getReason()).isEqualTo("Uygunsuz profil görseli");
        assertThat(captor.getValue().getModeratedAt()).isEqualTo(now);
    }

    @Test
    void nonAdminCannotUseModerationService() {
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, Role.COACH)));

        assertThatThrownBy(() -> service.removeProfileImage(2L, 42L, "reason"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getErrorCode()).isEqualTo("ADMIN_REQUIRED");
        verify(assets, never()).findById(42L);
        verify(logs, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminCannotModeratePrivateDocumentsThroughProfileImageAction() {
        User admin = user(1L, Role.ADMIN);
        User owner = user(7L, Role.STUDENT);
        MediaAsset document = profileImage(43L, owner);
        document.setMediaType(MediaType.DOCUMENT);
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(assets.findById(43L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.removeProfileImage(1L, 43L, "reason"))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getErrorCode())
                .isEqualTo("MEDIA_MODERATION_TARGET_INVALID");
        verify(storageDeletion, never()).deleteAfterCommit(document.getId(), document.getObjectKey());
    }

    private MediaAsset profileImage(Long id, User owner) {
        MediaAsset asset = new MediaAsset();
        ReflectionTestUtils.setField(asset, "id", id);
        asset.setOwner(owner);
        asset.setObjectKey("public/profile-images/" + id + ".jpg");
        asset.setMediaType(MediaType.PROFILE_IMAGE);
        asset.setStatus(MediaStatus.ACTIVE);
        return asset;
    }

    private User user(Long id, Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        return user;
    }
}
