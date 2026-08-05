package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.PrivacyPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrivacyPreferenceRepository extends JpaRepository<PrivacyPreference, Long> {
    Optional<PrivacyPreference> findByUserId(Long userId);
}
