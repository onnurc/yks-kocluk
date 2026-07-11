package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

public record IyzicoWebhookRequest(
        @NotNull(message = "paymentId cannot be null")
        Long paymentId,
        @NotNull(message = "status cannot be null")
        String status,
        String providerReference
) {}
