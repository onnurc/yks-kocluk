package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.MarketingPreference;
import com.ykskocluk.demo.enums.MarketingChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketingPreferenceRepository extends JpaRepository<MarketingPreference, Long> {
    List<MarketingPreference> findByUserId(Long userId);
    Optional<MarketingPreference> findByUserIdAndChannel(Long userId, MarketingChannel channel);
}
