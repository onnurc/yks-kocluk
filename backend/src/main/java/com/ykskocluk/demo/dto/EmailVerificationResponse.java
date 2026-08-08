package com.ykskocluk.demo.dto;

import java.time.Instant;

public record EmailVerificationResponse(boolean emailVerified, Instant nextResendAt) {
}
