package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.service.MediaService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Stable, shareable address for a PUBLIC media asset. Every request redirects to a newly signed
 * storage URL, so the address embedded in profile responses never goes stale the way a signed URL
 * would, and clients need no credentials to render it (an {@code <img src>} cannot send any).
 */
@RestController
@RequestMapping("/api/v1/public/media")
public class PublicMediaController {
    private final MediaService mediaService;

    public PublicMediaController(MediaService mediaService) { this.mediaService = mediaService; }

    @GetMapping("/{assetId}")
    public ResponseEntity<Void> redirectToAsset(@PathVariable Long assetId) {
        // Never cached: the redirect target expires, so a cached copy would eventually point at a
        // signature the storage provider rejects.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(mediaService.publicPresignedUrl(assetId)))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
