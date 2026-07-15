package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ConsentType;
import java.time.Instant;

/**
 * Response after recording a user's consent.
 */
public record ConsentResponse(
        Long consentId,
        ConsentType consentType,
        String documentVersion,
        Instant acceptedAt
) {
}
