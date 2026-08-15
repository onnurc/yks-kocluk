package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Track;

import java.util.Set;

/** Public coach profile without internal user identity or moderation fields. */
public record PublicCoachDetailResponse(
        Long id,
        String fullName,
        String headline,
        String bio,
        String universityName,
        String department,
        Integer graduationYear,
        Set<Track> tracks,
        Double rating,
        int totalSessions,
        boolean acceptingNewStudents,
        String profileImageUrl,
        String introVideoUrl
) {
    public static PublicCoachDetailResponse from(CoachDetailResponse coach) {
        return new PublicCoachDetailResponse(coach.id(), coach.fullName(), coach.headline(), coach.bio(),
                coach.universityName(), coach.department(), coach.graduationYear(), coach.tracks(), coach.rating(),
                coach.totalSessions(), coach.acceptingNewStudents(), coach.profileImageUrl(), coach.introVideoUrl());
    }
}
