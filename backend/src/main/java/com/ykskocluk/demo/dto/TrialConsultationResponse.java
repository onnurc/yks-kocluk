package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.TrialConsultationStatus;
import java.time.Instant;

public record TrialConsultationResponse(
        Long id,
        Long coachProfileId,
        String coachName,
        Long studentId,
        String studentName,
        Long availabilityId,
        Instant startsAt,
        Instant endsAt,
        TrialConsultationStatus status,
        String meetingUrl,
        Instant requestedAt,
        Instant updatedAt
) {}
