package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "Kod zorunludur")
        @Pattern(regexp = "^[0-9]{6}$", message = "Kod tam olarak 6 rakam olmalıdır")
        String code
) {
}
