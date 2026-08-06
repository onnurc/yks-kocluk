package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        UserStatus status,
        java.time.LocalDate dateOfBirth,
        boolean legalOnboardingCompleted,
        boolean hasLocalPassword,
        java.time.Instant passwordChangedAt
) {
    public UserResponse(Long id, String email, String fullName, Role role, UserStatus status) {
        this(id, email, fullName, role, status, null, false, false, null);
    }

    public UserResponse(Long id, String email, String fullName, Role role, UserStatus status,
                        java.time.LocalDate dateOfBirth) {
        this(id, email, fullName, role, status, dateOfBirth, false, false, null);
    }
}
