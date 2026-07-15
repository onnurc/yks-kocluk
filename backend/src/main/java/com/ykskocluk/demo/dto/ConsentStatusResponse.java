package com.ykskocluk.demo.dto;

/**
 * Response for legal consent status checks (Phase 7).
 */
public record ConsentStatusResponse(
        String currentVersion,
        boolean hasConsented,
        String status
) {
}
