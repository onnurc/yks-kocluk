package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.AvailabilityPurpose;

import java.time.Instant;

public record AvailabilityResponse(
        Long id,
        Long coachProfileId,
        Instant startTime,
        Instant endTime,
        boolean booked,
        AvailabilityPurpose purpose
) {
}
