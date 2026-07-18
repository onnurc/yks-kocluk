package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuth2ExchangeRequest(
        @NotBlank(message = "Kod boş olamaz")
        @Size(max = 256, message = "Geçersiz kod uzunluğu")
        String code
) {
}
