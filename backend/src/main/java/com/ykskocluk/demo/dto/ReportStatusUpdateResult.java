package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ReportStatus;

/**
 * Internal result from admin report status update — carries the API response alongside
 * the reporter's email and transition metadata for best-effort notification dispatch.
 */
public record ReportStatusUpdateResult(
        ReportResponse response,
        String reporterEmail,
        boolean transitioned,
        ReportStatus newStatus
) {
}
