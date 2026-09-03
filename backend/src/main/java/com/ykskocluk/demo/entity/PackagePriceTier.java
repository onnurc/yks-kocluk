package com.ykskocluk.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "package_price_tiers", uniqueConstraints =
        @UniqueConstraint(name = "uq_package_price_tier", columnNames = {"package_id", "months_remaining"}))
@Getter
@Setter
@NoArgsConstructor
public class PackagePriceTier extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private Package pkg;

    @Column(name = "months_remaining", nullable = false)
    private int monthsRemaining;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
}
