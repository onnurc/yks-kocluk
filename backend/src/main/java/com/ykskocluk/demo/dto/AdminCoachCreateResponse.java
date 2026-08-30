package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.UserStatus;

public record AdminCoachCreateResponse(
        Long userId,
        Long coachProfileId,
        String fullName,
        String email,
        UserStatus accountStatus,
        CoachProfileStatus profileStatus
) { }
