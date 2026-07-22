package com.ykskocluk.demo.dto;

/**
 * Internal result from report creation — carries the API response alongside the reporter's
 * email for best-effort notification dispatch in the controller layer.
 */
public record ReportCreationResult(
        ReportResponse response,
        String reporterEmail
) {
}
