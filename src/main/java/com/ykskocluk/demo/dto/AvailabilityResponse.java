package com.ykskocluk.demo.dto;

import java.time.Instant;

public record AvailabilityResponse(
        Long id,
        Long coachProfileId,
        Instant startTime,
        Instant endTime,
        boolean booked
) {
}
