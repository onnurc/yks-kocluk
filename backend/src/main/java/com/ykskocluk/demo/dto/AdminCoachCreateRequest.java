package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.validation.StrictEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCoachCreateRequest(
        @NotBlank(message = "Ad Soyad boş olamaz")
        @Size(max = 150, message = "Ad Soyad en fazla 150 karakter olabilir")
        String fullName,

        @NotBlank(message = "E-posta boş olamaz")
        @StrictEmail
        @Size(max = 255, message = "E-posta en fazla 255 karakter olabilir")
        String email
) { }
