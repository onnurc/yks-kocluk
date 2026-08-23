package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UniversityRepository extends JpaRepository<University, Long> {

    boolean existsByName(String name);

    Optional<University> findByNameIgnoreCase(String name);
}
