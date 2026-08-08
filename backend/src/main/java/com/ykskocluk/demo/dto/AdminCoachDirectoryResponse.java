package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.UserStatus;
import java.time.Instant;

public record AdminCoachDirectoryResponse(Long id, Long userId, Long coachProfileId, String name, String email,
                                          CoachProfileStatus status, CoachProfileStatus approvalState, UserStatus accountStatus,
                                          Long universityId, String university, String department,
                                          boolean publiclyVisible, Instant createdAt) {}
