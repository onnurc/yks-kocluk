package com.ykskocluk.demo.dto;

import java.time.Instant;

public record ExplicitConsentWithdrawalResponse(
        boolean legalOnboardingCompleted,
        Instant withdrawnAt
) { }
