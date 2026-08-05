package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.AccountDeletionStatus;

import java.time.Instant;

public record AccountDeletionResponse(
        AccountDeletionStatus status,
        Instant requestedAt,
        Instant completedAt
) { }
