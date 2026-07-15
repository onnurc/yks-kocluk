package com.ykskocluk.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Infrastructure entity backing the Phase 0 vertical slice
 * (controller → service → repository → DB). Extends {@link BaseEntity} like every
 * other entity, so it also exercises the auditing/versioning wiring.
 */
@Entity
@Table(name = "health_check")
@Getter
@NoArgsConstructor
public class HealthCheck extends BaseEntity {

    @Column(nullable = false)
    private String status;
}
