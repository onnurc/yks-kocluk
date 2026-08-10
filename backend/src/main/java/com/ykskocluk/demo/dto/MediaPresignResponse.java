package com.ykskocluk.demo.dto;

import java.time.Instant;
import java.util.Map;

public record MediaPresignResponse(Long assetId, String objectKey, String uploadUrl, Instant expiresAt,
                                   Map<String, String> requiredHeaders) {}
