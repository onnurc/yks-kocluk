package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

public record RefundRequestCreateRequest(@NotNull(message = "Abonelik seçilmelidir") Long subscriptionId) {
}
