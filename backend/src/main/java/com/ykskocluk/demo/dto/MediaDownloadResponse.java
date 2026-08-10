package com.ykskocluk.demo.dto;

import java.time.Instant;

public record MediaDownloadResponse(String url, Instant expiresAt) {}
