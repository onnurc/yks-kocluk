package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.HealthResponse;
import com.ykskocluk.demo.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 0 proof endpoint. Public on purpose (permit-all). Real role-based access
 * (@PreAuthorize) is applied per-controller starting in Phase 1.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public HealthResponse health() {
        return healthService.check();
    }
}
