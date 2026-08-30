package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.MarketingPreference;
import com.ykskocluk.demo.enums.MarketingChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketingPreferenceRepository extends JpaRepository<MarketingPreference, Long> {
    List<MarketingPreference> findByUserId(Long userId);
    Optional<MarketingPreference> findByUserIdAndChannel(Long userId, MarketingChannel channel);

    @Modifying
    @Query("delete from MarketingPreference p where p.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
