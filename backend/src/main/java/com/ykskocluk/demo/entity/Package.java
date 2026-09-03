package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.PackageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A platform-defined coaching plan. Pricing is uniform/platform-set (no coach pricing).
 * {@code price} is informational until Phase 8 wires real payment.
 */
@Entity
@Table(name = "packages")
@Getter
@Setter
@NoArgsConstructor
public class Package extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "weekly_sessions", nullable = false)
    private int weeklySessions;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", length = 30, unique = true)
    private PackageType packageType;

    @Column(name = "duration_months")
    private Integer durationMonths;

    @Column(name = "evaluation_sessions_per_month", nullable = false)
    private int evaluationSessionsPerMonth = 1;

    @Column(name = "weekly_sessions_per_month", nullable = false)
    private int weeklySessionsPerMonth = 4;

    public int getTotalSessionsPerMonth() {
        return evaluationSessionsPerMonth + weeklySessionsPerMonth;
    }
}
