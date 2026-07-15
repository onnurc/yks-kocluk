package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

public record SubscriptionCreateRequest(

        @NotNull(message = "Koç seçilmeli")
        Long coachId,

        @NotNull(message = "Paket seçilmeli")
        Long packageId
) {
}
