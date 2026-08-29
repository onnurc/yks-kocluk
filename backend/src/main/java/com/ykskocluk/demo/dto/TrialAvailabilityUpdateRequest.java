package com.ykskocluk.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record TrialAvailabilityUpdateRequest(
        @NotNull(message = "Uygunluk seçimi zorunludur")
        @Size(max = 112, message = "En fazla 112 görüşme saati seçilebilir")
        List<@NotNull(message = "Başlangıç zamanı boş olamaz") Instant> startTimes
) { }
