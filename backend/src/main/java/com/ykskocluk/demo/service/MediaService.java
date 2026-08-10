package com.ykskocluk.demo.service;

import com.ykskocluk.demo.config.MediaPolicyProperties;
import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.entity.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.exception.ApiException;
import com.ykskocluk.demo.repository.*;
import com.ykskocluk.demo.storage.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm");
    private static final Set<String> DOCUMENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

    private final MediaAssetRepository assets;
    private final UserRepository users;
    private final CoachProfileRepository coaches;
    private final StudentProfileRepository students;
    private final StorageService storage;
    private final MediaPolicyProperties policy;
    private final MediaAssetUrlResolver urlResolver;

    public MediaService(MediaAssetRepository assets, UserRepository users, CoachProfileRepository coaches,
                        StudentProfileRepository students, StorageService storage, MediaPolicyProperties policy,
                        MediaAssetUrlResolver urlResolver) {
        this.assets = assets; this.users = users; this.coaches = coaches; this.students = students;
        this.storage = storage; this.policy = policy; this.urlResolver = urlResolver;
    }

    @Transactional
    public MediaPresignResponse presign(Long userId, MediaPresignRequest request) {
        User owner = requireUser(userId);
        String contentType = request.contentType().trim().toLowerCase(java.util.Locale.ROOT);
        validatePolicy(owner, request.mediaType(), contentType, request.sizeBytes());
        MediaVisibility visibility = request.mediaType() == MediaType.DOCUMENT ? MediaVisibility.PRIVATE : MediaVisibility.PUBLIC;
        String objectKey = generateKey(owner, request.mediaType(), contentType);

        MediaAsset asset = new MediaAsset();
        asset.setOwner(owner); asset.setObjectKey(objectKey);
        asset.setOriginalFilename(safeFilename(request.originalFilename()));
        asset.setContentType(contentType); asset.setSizeBytes(request.sizeBytes());
        asset.setMediaType(request.mediaType()); asset.setVisibility(visibility); asset.setStatus(MediaStatus.PENDING_UPLOAD);
        assets.save(asset);

        StorageService.UploadTarget target = storage.createPresignedUpload(objectKey, contentType, request.sizeBytes());
        return new MediaPresignResponse(asset.getId(), objectKey, target.url(), target.expiresAt(), target.requiredHeaders());
    }

    @Transactional
    public MediaAssetResponse complete(Long userId, Long assetId) {
        MediaAsset asset = requireAsset(assetId);
        requireOwner(userId, asset);
        if (asset.getStatus() != MediaStatus.PENDING_UPLOAD) {
            throw new ApiException(HttpStatus.CONFLICT, "MEDIA_STATUS_INVALID", "Only pending uploads can be completed");
        }
        StorageService.StoredObjectMetadata actual = storage.headObject(asset.getObjectKey());
        if (!actual.exists()) throw new ApiException(HttpStatus.CONFLICT, "MEDIA_UPLOAD_NOT_FOUND", "Uploaded object was not found");
        if (actual.sizeBytes() != asset.getSizeBytes() || !asset.getContentType().equalsIgnoreCase(actual.contentType())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEDIA_METADATA_MISMATCH", "Uploaded object metadata does not match the presign request");
        }
        asset.setStatus(MediaStatus.ACTIVE);
        attachToProfile(asset);
        return response(asset);
    }

    @Transactional(readOnly = true)
    public MediaDownloadResponse download(Long requesterId, Long assetId) {
        User requester = requireUser(requesterId);
        MediaAsset asset = requireActiveAsset(assetId);
        if (asset.getVisibility() == MediaVisibility.PUBLIC) {
            return new MediaDownloadResponse(urlResolver.publicUrl(asset), null);
        }
        if (!asset.getOwner().getId().equals(requesterId) && requester.getRole() != Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MEDIA_OWNER_INVALID", "You cannot access this media asset");
        }
        StorageService.DownloadTarget target = storage.createPresignedDownload(asset.getObjectKey());
        return new MediaDownloadResponse(target.url(), target.expiresAt());
    }

    @Transactional
    public void delete(Long requesterId, Long assetId) {
        MediaAsset asset = requireAsset(assetId);
        requireOwner(requesterId, asset);
        if (asset.getStatus() == MediaStatus.DELETED) return;
        storage.deleteObject(asset.getObjectKey());
        detachFromProfiles(asset);
        asset.setStatus(MediaStatus.DELETED);
    }

    private void validatePolicy(User owner, MediaType type, String contentType, long size) {
        Set<String> allowed;
        long max;
        switch (type) {
            case PROFILE_IMAGE -> { allowed = IMAGE_TYPES; max = policy.profileImageMaxBytes(); requireProfileOwner(owner); }
            case COACH_INTRO_VIDEO -> {
                allowed = VIDEO_TYPES; max = policy.coachVideoMaxBytes();
                if (owner.getRole() != Role.COACH || !coaches.existsByUserId(owner.getId()))
                    throw new ApiException(HttpStatus.FORBIDDEN, "MEDIA_OWNER_INVALID", "Coach intro video requires the owner's coach profile");
            }
            case DOCUMENT -> { allowed = DOCUMENT_TYPES; max = policy.documentMaxBytes(); }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_TYPE_NOT_ALLOWED", "Unsupported media type");
        }
        if (!allowed.contains(contentType)) throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_TYPE_NOT_ALLOWED", "Content type is not allowed for this media type");
        if (size > max) throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_SIZE_EXCEEDED", "Media exceeds the configured size limit", Map.of("maxBytes", max));
    }

    private void requireProfileOwner(User owner) {
        boolean exists = owner.getRole() == Role.COACH ? coaches.existsByUserId(owner.getId())
                : owner.getRole() == Role.STUDENT && students.existsByUserId(owner.getId());
        if (!exists) throw new ApiException(HttpStatus.FORBIDDEN, "MEDIA_OWNER_INVALID", "Profile image requires the owner's profile");
    }

    private String generateKey(User owner, MediaType type, String contentType) {
        String folder = switch (type) {
            case PROFILE_IMAGE -> "public/profile-images/";
            case COACH_INTRO_VIDEO -> "public/coach-videos/";
            case DOCUMENT -> "private/documents/";
        };
        return folder + owner.getId() + "/" + UUID.randomUUID() + extension(contentType);
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4"; case "video/webm" -> ".webm"; case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return null;
        String base = filename.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\u0000]", "");
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private void attachToProfile(MediaAsset asset) {
        if (asset.getMediaType() == MediaType.DOCUMENT) return;
        if (asset.getOwner().getRole() == Role.COACH) {
            CoachProfile profile = coaches.findByUserId(asset.getOwner().getId()).orElseThrow();
            if (asset.getMediaType() == MediaType.PROFILE_IMAGE) {
                markReplaced(profile.getProfileImageAsset()); profile.setProfileImageAsset(asset);
            } else { markReplaced(profile.getIntroVideoAsset()); profile.setIntroVideoAsset(asset); }
        } else if (asset.getMediaType() == MediaType.PROFILE_IMAGE) {
            StudentProfile profile = students.findByUserId(asset.getOwner().getId()).orElseThrow();
            markReplaced(profile.getProfileImageAsset()); profile.setProfileImageAsset(asset);
        }
    }

    private void markReplaced(MediaAsset old) { if (old != null && old.getStatus() == MediaStatus.ACTIVE) old.setStatus(MediaStatus.DELETED); }

    private void detachFromProfiles(MediaAsset asset) {
        coaches.findByUserId(asset.getOwner().getId()).ifPresent(p -> {
            if (sameAsset(p.getProfileImageAsset(), asset)) p.setProfileImageAsset(null);
            if (sameAsset(p.getIntroVideoAsset(), asset)) p.setIntroVideoAsset(null);
        });
        students.findByUserId(asset.getOwner().getId()).ifPresent(p -> {
            if (sameAsset(p.getProfileImageAsset(), asset)) p.setProfileImageAsset(null);
        });
    }

    private boolean sameAsset(MediaAsset left, MediaAsset right) {
        return left != null && left.getId() != null && left.getId().equals(right.getId());
    }

    private User requireUser(Long id) { return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found")); }
    private MediaAsset requireAsset(Long id) { return assets.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Media asset not found")); }
    private MediaAsset requireActiveAsset(Long id) { MediaAsset a = requireAsset(id); if (a.getStatus() != MediaStatus.ACTIVE) throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "Active media asset not found"); return a; }
    private void requireOwner(Long userId, MediaAsset asset) { if (!asset.getOwner().getId().equals(userId)) throw new ApiException(HttpStatus.FORBIDDEN, "MEDIA_OWNER_INVALID", "You do not own this media asset"); }
    private MediaAssetResponse response(MediaAsset a) { return new MediaAssetResponse(a.getId(), a.getMediaType(), a.getVisibility(), a.getStatus(), a.getContentType(), a.getSizeBytes(), urlResolver.publicUrl(a)); }
}
