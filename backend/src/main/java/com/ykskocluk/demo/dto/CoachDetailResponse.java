package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Track;

import java.util.Set;

/**
 * Public coach detail (APPROVED only). Same derived fields as the summary, plus the
 * fuller profile text. No internal fields are exposed.
 */
public record CoachDetailResponse(
        Long id,
        Long userId,
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
        String introVideoEmbedUrl
) {
}
