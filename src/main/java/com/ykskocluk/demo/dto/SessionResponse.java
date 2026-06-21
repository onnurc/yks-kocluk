package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.SessionStatus;

import java.time.Instant;

public record SessionResponse(
        Long id,
        Long coachProfileId,
        String coachName,
        String studentName,
        Long availabilityId,
        SessionStatus status,
        Instant startTime,
        Instant endTime,
        String meetLink
) {
}
