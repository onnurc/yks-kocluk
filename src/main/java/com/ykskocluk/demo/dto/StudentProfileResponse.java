package com.ykskocluk.demo.dto;

public record StudentProfileResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String gradeLevel,
        String city
) {
}
