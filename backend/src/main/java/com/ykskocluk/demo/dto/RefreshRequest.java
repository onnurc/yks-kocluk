package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank(message = "Yenileme jetonu boş olamaz")
        String refreshToken
) {
}
