package com.ykskocluk.demo.dto;

import java.time.Instant;

public record PrivacyPreferencesResponse(
        boolean necessaryAllowed,
        boolean analyticsAllowed,
        boolean marketingAllowed,
        Long cookiePolicyDocumentId,
        String policyVersion,
        Instant grantedAt,
        Instant updatedAt
) { }
