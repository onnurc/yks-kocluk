package com.ykskocluk.demo.dto;

import java.time.Instant;

public record CoachDashboardEventResponse(Long id, String type, Instant startsAt, Instant endsAt,
                                          Long studentId, String studentName) {}
