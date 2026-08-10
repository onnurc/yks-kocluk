package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaType;
import com.ykskocluk.demo.enums.MediaVisibility;

public record MediaAssetResponse(Long id, MediaType mediaType, MediaVisibility visibility, MediaStatus status,
                                 String contentType, long sizeBytes, String url) {}
