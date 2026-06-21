package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.HealthResponse;
import com.ykskocluk.demo.entity.HealthCheck;
import com.ykskocluk.demo.repository.HealthCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthService {

    private final HealthCheckRepository healthCheckRepository;

    public HealthService(HealthCheckRepository healthCheckRepository) {
        this.healthCheckRepository = healthCheckRepository;
    }

    @Transactional(readOnly = true)
    public HealthResponse check() {
        // Reads the seeded row to prove the repository → DB hop actually works.
        String status = healthCheckRepository.findAll().stream()
                .findFirst()
                .map(HealthCheck::getStatus)
                .orElse("DOWN");
        return new HealthResponse(status);
    }
}
