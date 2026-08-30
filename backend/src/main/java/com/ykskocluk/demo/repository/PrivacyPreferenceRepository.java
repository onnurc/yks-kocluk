package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.PrivacyPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PrivacyPreferenceRepository extends JpaRepository<PrivacyPreference, Long> {
    Optional<PrivacyPreference> findByUserId(Long userId);

    @Modifying
    @Query("delete from PrivacyPreference p where p.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
