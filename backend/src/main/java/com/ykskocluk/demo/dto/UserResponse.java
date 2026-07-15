package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role,
        UserStatus status,
        java.time.LocalDate dateOfBirth
) {
    public UserResponse(Long id, String email, String fullName, Role role, UserStatus status) {
        this(id, email, fullName, role, status, null);
    }
}
