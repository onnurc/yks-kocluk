package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.HealthCheck;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, Long> {
}
