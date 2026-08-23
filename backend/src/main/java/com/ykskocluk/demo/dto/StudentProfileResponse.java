package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ExamSession;
import com.ykskocluk.demo.enums.Track;

public record StudentProfileResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String gradeLevel,
        String city,
        Integer examYear,
        Track yksScoreType,
        ExamSession examSession,
        String targetUniversity,
        String targetDepartment,
        String profileImageUrl
) {
}
