package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for admin to update safety report status.
 */
public record AdminReportStatusUpdateRequest(
        @NotNull(message = "Durum alanı boş olamaz")
        ReportStatus status
) {
}
