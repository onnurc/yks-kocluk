package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * DTO for requesting an admin refund.
 */
public record RefundRequest(
        @NotNull(message = "Amount cannot be null")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,
        
        @Size(max = 2000, message = "Refund reason cannot exceed 2000 characters")
        String reason
) {
}
