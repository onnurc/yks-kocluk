package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(

        @NotBlank(message = "Yenileme jetonu boş olamaz")
        String refreshToken
) {
}
