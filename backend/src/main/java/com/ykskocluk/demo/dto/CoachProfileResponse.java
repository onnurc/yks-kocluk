package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Track;

import java.util.Set;

public record CoachProfileResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String headline,
        String bio,
        Long universityId,
        String universityName,
        String department,
        Integer graduationYear,
        CoachProfileStatus status,
        String rejectionReason,
        Set<Track> tracks,
        int activeStudentCount,
        int maxStudentCapacity,
        boolean payoutAccountReady,
        String profileImageUrl,
        String introVideoUrl
) {
}
