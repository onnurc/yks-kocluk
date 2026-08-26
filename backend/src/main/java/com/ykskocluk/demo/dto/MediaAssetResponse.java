package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaType;
import com.ykskocluk.demo.enums.MediaVisibility;

/**
 * Completion response. {@code url} is the stable opaque backend URL only for ACTIVE PUBLIC media;
 * it is always null for PRIVATE media. Use {@code GET /media/{id}/download-url} for an authorized,
 * expiring PRIVATE download URL.
 */
public record MediaAssetResponse(Long id, MediaType mediaType, MediaVisibility visibility, MediaStatus status,
                                 String contentType, long sizeBytes, String url) {}
