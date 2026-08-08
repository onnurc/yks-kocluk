package com.ykskocluk.demo.dto;

public record CoachDashboardSummaryResponse(
        long activeStudentCount,
        long completedSessionsThisMonth,
        long upcomingSessionCount,
        long unreadMessageCount,
        boolean availabilityConfigured,
        CoachDashboardEventResponse nextSession,
        long pendingTrialConsultationCount
) {}
