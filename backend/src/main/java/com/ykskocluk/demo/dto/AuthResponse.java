package com.ykskocluk.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record AuthResponse(
        String accessToken,
        @JsonIgnore
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
