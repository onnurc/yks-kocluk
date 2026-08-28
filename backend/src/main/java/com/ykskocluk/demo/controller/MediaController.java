package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.security.ratelimit.MediaPresignRateLimitService;
import com.ykskocluk.demo.security.ratelimit.AuthenticatedActionRateLimitService;
import com.ykskocluk.demo.service.MediaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaService mediaService;
    private final MediaPresignRateLimitService presignRateLimit;
    private final AuthenticatedActionRateLimitService actionRateLimit;
    public MediaController(MediaService mediaService, MediaPresignRateLimitService presignRateLimit,
                           AuthenticatedActionRateLimitService actionRateLimit) {
        this.mediaService = mediaService;
        this.presignRateLimit = presignRateLimit;
        this.actionRateLimit = actionRateLimit;
    }

    @PostMapping("/uploads/presign") @ResponseStatus(HttpStatus.CREATED)
    public MediaPresignResponse presign(@AuthenticationPrincipal Long userId, @Valid @RequestBody MediaPresignRequest request) {
        presignRateLimit.check(userId);
        return mediaService.presign(userId, request);
    }

    @PostMapping("/uploads/{assetId}/complete")
    public MediaAssetResponse complete(@AuthenticationPrincipal Long userId, @PathVariable Long assetId) {
        actionRateLimit.checkMediaComplete(userId);
        return mediaService.complete(userId, assetId);
    }

    @GetMapping("/{assetId}/download-url")
    public MediaDownloadResponse download(@AuthenticationPrincipal Long userId, @PathVariable Long assetId) {
        return mediaService.download(userId, assetId);
    }

    @DeleteMapping("/{assetId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long assetId) { mediaService.delete(userId, assetId); }
}
