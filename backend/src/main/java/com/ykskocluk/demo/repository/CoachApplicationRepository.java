package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.CoachApplication;
import com.ykskocluk.demo.enums.CoachApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachApplicationRepository extends JpaRepository<CoachApplication, Long> {

    Page<CoachApplication> findByStatus(CoachApplicationStatus status, Pageable pageable);

    boolean existsByEmailIgnoreCaseAndStatus(String email, CoachApplicationStatus status);

    long countByStatus(CoachApplicationStatus status);
}
