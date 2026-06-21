package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(

        @NotBlank(message = "Red gerekçesi boş olamaz")
        @Size(max = 500, message = "Red gerekçesi en fazla 500 karakter olabilir")
        String reason
) {
}
