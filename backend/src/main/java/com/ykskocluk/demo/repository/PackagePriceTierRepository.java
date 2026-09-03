package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.PackagePriceTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackagePriceTierRepository extends JpaRepository<PackagePriceTier, Long> {
    List<PackagePriceTier> findByPkgIdOrderByMonthsRemainingAsc(Long packageId);
    Optional<PackagePriceTier> findByPkgIdAndMonthsRemaining(Long packageId, int monthsRemaining);
    void deleteByPkgIdAndMonthsRemaining(Long packageId, int monthsRemaining);
}
