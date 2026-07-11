package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO for requesting an admin refund.
 */
public record RefundRequest(
        @NotNull(message = "Amount cannot be null")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,
        
        String reason
) {
}
