package com.ykskocluk.demo.dto;

import java.time.Instant;

/** Authorized, short-lived storage URL for PRIVATE media (and compatible authenticated clients). */
public record MediaDownloadResponse(String url, Instant expiresAt) {}
