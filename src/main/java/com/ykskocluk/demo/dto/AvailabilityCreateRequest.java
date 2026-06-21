package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AvailabilityCreateRequest(

        @NotNull(message = "Başlangıç zamanı zorunlu")
        Instant startTime,

        @NotNull(message = "Bitiş zamanı zorunlu")
        Instant endTime
) {
}
