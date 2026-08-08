package com.ykskocluk.demo.dto;

import java.time.Instant;

public record AdminOperationalSessionResponse(Long id, String type, Long coachProfileId,
                                              Long coachUserId, String coachName, Long studentId,
                                              String studentName, Instant startsAt, Instant endsAt,
                                              String status, Long subscriptionId) {}
