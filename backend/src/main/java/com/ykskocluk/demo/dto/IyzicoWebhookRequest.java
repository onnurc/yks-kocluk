package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IyzicoWebhookRequest(
        @NotNull(message = "paymentId cannot be null")
        Long paymentId,
        @NotBlank(message = "status cannot be blank")
        @Size(max = 32, message = "status must be at most 32 characters")
        String status,
        @Size(max = 512, message = "providerReference must be at most 512 characters")
        String providerReference,
        @Size(max = 64, message = "iyziEventType must be at most 64 characters")
        String iyziEventType,
        @Size(max = 128, message = "paymentConversationId must be at most 128 characters")
        String paymentConversationId
) {
    public IyzicoWebhookRequest(Long paymentId, String status, String providerReference) {
        this(paymentId, status, providerReference, null, null);
    }
}
