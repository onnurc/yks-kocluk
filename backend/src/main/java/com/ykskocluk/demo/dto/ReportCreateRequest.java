package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for submitting a safety report.
 */
public record ReportCreateRequest(
        @NotNull(message = "Target type cannot be null")
        ReportTargetType targetType,

        @NotNull(message = "Target ID cannot be null")
        Long targetId,

        @NotBlank(message = "Reason cannot be blank")
        @Size(max = 2000, message = "Reason cannot exceed 2000 characters")
        String reason,

        @Size(max = 4000, message = "Details cannot exceed 4000 characters")
        String details
) {
}
