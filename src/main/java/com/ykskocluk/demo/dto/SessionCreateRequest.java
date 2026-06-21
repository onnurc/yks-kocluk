package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

public record SessionCreateRequest(

        @NotNull(message = "Uygunluk seçilmeli")
        Long availabilityId
) {
}
