package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AdminMediaRemovalRequest;
import com.ykskocluk.demo.service.MediaModerationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMediaController {
    private final MediaModerationService moderationService;

    public AdminMediaController(MediaModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping("/{assetId}/remove-profile-image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeProfileImage(@AuthenticationPrincipal Long adminUserId,
                                   @PathVariable Long assetId,
                                   @Valid @RequestBody AdminMediaRemovalRequest request) {
        moderationService.removeProfileImage(adminUserId, assetId, request.reason());
    }
}
