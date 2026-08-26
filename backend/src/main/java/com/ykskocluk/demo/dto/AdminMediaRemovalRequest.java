package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMediaRemovalRequest(
        @NotBlank(message = "Moderasyon gerekçesi zorunludur")
        @Size(max = 1000, message = "Moderasyon gerekçesi en fazla 1000 karakter olabilir")
        String reason
) {
}
