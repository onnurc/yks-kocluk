package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequestRejectRequest(
        @NotBlank(message = "Ret gerekçesi boş olamaz")
        @Size(min = 3, max = 2000, message = "Ret gerekçesi 3 ile 2000 karakter arasında olmalıdır")
        String reason) {
}
