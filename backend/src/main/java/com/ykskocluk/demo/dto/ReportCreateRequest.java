package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for submitting a safety report.
 */
public record ReportCreateRequest(
        @NotNull(message = "Target type cannot be null")
        ReportTargetType targetType,

        @NotNull(message = "Target ID cannot be null")
        Long targetId,

        @NotBlank(message = "Reason cannot be blank")
        String reason,

        String details
) {
}
