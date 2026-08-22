package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.entity.CoachApplication;
import com.ykskocluk.demo.enums.CoachApplicationStatus;

import java.time.Instant;

public record CoachApplicationResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String experience,
        CoachApplicationStatus status,
        Instant createdAt,
        Instant reviewedAt,
        String reviewNote,
        Long linkedUserId
) {
    public static CoachApplicationResponse from(CoachApplication application) {
        return new CoachApplicationResponse(
                application.getId(),
                application.getFullName(),
                application.getEmail(),
                application.getPhone(),
                application.getExperience(),
                application.getStatus(),
                application.getCreatedAt(),
                application.getReviewedAt(),
                application.getReviewNote(),
                application.getLinkedUser() != null ? application.getLinkedUser().getId() : null
        );
    }
}
