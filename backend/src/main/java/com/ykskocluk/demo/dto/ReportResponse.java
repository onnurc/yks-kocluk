package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ReportStatus;
import com.ykskocluk.demo.enums.ReportTargetType;
import java.time.Instant;

/**
 * Response after creating or listing safety reports.
 */
public record ReportResponse(
        Long id,
        Long reporterUserId,
        ReportTargetType targetType,
        Long targetId,
        String reason,
        String details,
        ReportStatus status,
        Instant createdAt,
        Instant reviewedAt,
        Long reviewedByAdminId
) {
}
