package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Role;
import com.ykskocluk.demo.enums.UserStatus;
import java.time.Instant;

public record AdminUserDirectoryResponse(Long id, String name, String email, Role role,
                                         UserStatus status, boolean emailVerified,
                                         boolean legalOnboardingCompleted, boolean anonymized,
                                         Instant createdAt) {}
