package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * Atomically creates the nullable baseline profile required by every STUDENT.
     * The role predicate prevents provisioning for unrelated roles; the unique user_id
     * constraint and ON CONFLICT make concurrent/repeated repair calls idempotent.
     */
    @Modifying
    @Query(value = """
            insert into student_profiles (user_id, created_at, updated_at, version)
            select u.id, current_timestamp, current_timestamp, 0
              from users u
             where u.id = :userId and u.role = 'STUDENT'
            on conflict (user_id) do nothing
            """, nativeQuery = true)
    int createBaselineIfMissing(@Param("userId") Long userId);
}
