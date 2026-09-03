package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record AdminExamSettingsRequest(
        @NotNull @Positive Integer examYear,
        @NotNull LocalDate examDate,
        boolean active
) {}
