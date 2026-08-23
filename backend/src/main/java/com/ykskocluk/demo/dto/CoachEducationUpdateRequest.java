package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CoachEducationUpdateRequest(
        @NotBlank(message = "Üniversite boş olamaz")
        @Size(max = 200, message = "Üniversite en fazla 200 karakter olabilir")
        String university,

        @Size(max = 150, message = "Bölüm en fazla 150 karakter olabilir")
        String department,

        @Positive(message = "YKS sıralaması pozitif bir sayı olmalı")
        Integer yksRanking
) {
}
