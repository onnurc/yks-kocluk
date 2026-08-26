package com.ykskocluk.demo.service;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.entity.MediaModerationLog;
import com.ykskocluk.demo.entity.StudentProfile;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class MediaModerationService {
    private final MediaAssetRepository assets;
    private final MediaModerationLogRepository logs;
    private final UserRepository users;
    private final CoachProfileRepository coaches;
    private final StudentProfileRepository students;
    private final AfterCommitStorageDeletionService storageDeletion;
    private final Clock clock;

    public MediaModerationService(MediaAssetRepository assets, MediaModerationLogRepository logs,
                                  UserRepository users, CoachProfileRepository coaches,
                                  StudentProfileRepository students,
                                  AfterCommitStorageDeletionService storageDeletion, Clock clock) {
        this.assets = assets;
        this.logs = logs;
        this.users = users;
        this.coaches = coaches;
        this.students = students;
        this.storageDeletion = storageDeletion;
        this.clock = clock;
    }

    @Transactional
    public void removeProfileImage(Long adminUserId, Long assetId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_MODERATION_REASON_REQUIRED",
                    "A moderation reason is required");
        }
        User admin = users.findById(adminUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Admin user not found"));
        if (admin.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "Admin role is required for media moderation");
        }
        MediaAsset asset = assets.findById(assetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media asset not found"));
        if (asset.getMediaType() != MediaType.PROFILE_IMAGE || asset.getStatus() != MediaStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "MEDIA_MODERATION_TARGET_INVALID",
                    "Only an active user profile image can be removed by this action");
        }

        detachCurrentProfileImage(asset);
        asset.setStatus(MediaStatus.DELETED);

        MediaModerationLog log = new MediaModerationLog();
        log.setAdmin(admin);
        log.setAsset(asset);
        log.setTargetOwner(asset.getOwner());
        log.setAction("REMOVE_PROFILE_IMAGE");
        log.setReason(reason.trim());
        log.setModeratedAt(clock.instant());
        logs.save(log);

        storageDeletion.deleteAfterCommit(asset.getObjectKey());
    }

    private void detachCurrentProfileImage(MediaAsset asset) {
        coaches.findByUserId(asset.getOwner().getId()).ifPresent(profile -> detach(profile, asset));
        students.findByUserId(asset.getOwner().getId()).ifPresent(profile -> detach(profile, asset));
    }

    private void detach(CoachProfile profile, MediaAsset asset) {
        if (sameAsset(profile.getProfileImageAsset(), asset)) profile.setProfileImageAsset(null);
    }

    private void detach(StudentProfile profile, MediaAsset asset) {
        if (sameAsset(profile.getProfileImageAsset(), asset)) profile.setProfileImageAsset(null);
    }

    private boolean sameAsset(MediaAsset left, MediaAsset right) {
        return left != null && left.getId() != null && left.getId().equals(right.getId());
    }
}
