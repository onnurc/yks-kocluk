package com.ykskocluk.demo.dto;

public record AdminConversationCoachResponse(Long coachId, String displayName,
                                               String profilePhotoUrl, long studentCount) {
}
