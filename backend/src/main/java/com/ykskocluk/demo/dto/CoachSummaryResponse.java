package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.Track;

import java.util.Set;

/**
 * Discovery list item. Public-safe — no internal fields (rejectionReason/payout/capacity
 * internals). {@code rating}/{@code totalSessions} are derived at runtime.
 */
public record CoachSummaryResponse(
        Long id,
        String fullName,
        String headline,
        String universityName,
        Set<Track> tracks,
        Double rating,
        int totalSessions,
        boolean acceptingNewStudents,
        String profileImageUrl,
        String introVideoEmbedUrl
) {
}
