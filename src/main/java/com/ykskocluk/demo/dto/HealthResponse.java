package com.ykskocluk.demo.dto;

/**
 * Phase 0 proof DTO. {@code status} is read from the DB (the health_check row) to
 * prove the full controller → service → repository → DB chain.
 */
public record HealthResponse(String status) {
}
