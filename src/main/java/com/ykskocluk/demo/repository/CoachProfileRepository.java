package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.CoachProfile;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.enums.Track;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CoachProfileRepository extends JpaRepository<CoachProfile, Long> {

    Optional<CoachProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Page<CoachProfile> findByStatus(CoachProfileStatus status, Pageable pageable);

    Optional<CoachProfile> findByIdAndStatus(Long id, CoachProfileStatus status);

    /**
     * Atomic capacity guard for the capacity race: increments only if there's room.
     * Returns rows affected — 0 means the coach is full (caller throws CONFLICT).
     * Bypasses optimistic @Version on purpose; the conditional WHERE + row lock is the guard.
     */
    @Modifying
    @Query("""
            update CoachProfile c set c.activeStudentCount = c.activeStudentCount + 1
             where c.id = :id and c.activeStudentCount < c.maxStudentCapacity
            """)
    int incrementActiveStudentCountIfRoom(@Param("id") Long id);

    /**
     * Discovery search over APPROVED coaches only. All filters are optional (null = ignore).
     * Track is matched via the CoachSubject join table (no string filtering).
     */
    @Query(value = """
            select c from CoachProfile c
              join c.user u
             where c.status = com.ykskocluk.demo.enums.CoachProfileStatus.APPROVED
               and (:universityId is null or c.university.id = :universityId)
               and (:track is null or exists (
                      select 1 from CoachSubject cs where cs.coachProfile = c and cs.track = :track))
               and (:q is null
                    or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))
                    or lower(c.headline) like lower(concat('%', cast(:q as string), '%')))
            """,
            countQuery = """
            select count(c) from CoachProfile c
              join c.user u
             where c.status = com.ykskocluk.demo.enums.CoachProfileStatus.APPROVED
               and (:universityId is null or c.university.id = :universityId)
               and (:track is null or exists (
                      select 1 from CoachSubject cs where cs.coachProfile = c and cs.track = :track))
               and (:q is null
                    or lower(u.fullName) like lower(concat('%', cast(:q as string), '%'))
                    or lower(c.headline) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<CoachProfile> search(@Param("universityId") Long universityId,
                              @Param("track") Track track,
                              @Param("q") String q,
                              Pageable pageable);
}
