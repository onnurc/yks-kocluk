package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

public record TrialConsultationCreateRequest(
        @NotNull(message = "Uygunluk seçilmeli") Long availabilityId
) {}
