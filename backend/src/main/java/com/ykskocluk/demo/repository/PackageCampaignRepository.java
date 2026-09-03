package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.PackageCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PackageCampaignRepository extends JpaRepository<PackageCampaign, Long> {
    Optional<PackageCampaign> findByPkgId(Long packageId);
}
