package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.University;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UniversityRepository extends JpaRepository<University, Long> {

    boolean existsByName(String name);
}
