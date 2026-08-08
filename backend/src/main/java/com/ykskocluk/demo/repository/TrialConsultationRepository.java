package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.TrialConsultation;
import com.ykskocluk.demo.enums.TrialConsultationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TrialConsultationRepository extends JpaRepository<TrialConsultation, Long> {
    List<TrialConsultation> findByStudentIdOrderByStartTimeDesc(Long studentId);
    List<TrialConsultation> findByCoachProfileIdOrderByStartTimeDesc(Long coachProfileId);
    Optional<TrialConsultation> findByIdAndCoachProfileId(Long id, Long coachProfileId);

    boolean existsByStudentIdAndCoachProfileIdAndStatusIn(Long studentId, Long coachProfileId,
                                                           Collection<TrialConsultationStatus> statuses);

    @Query("""
            select count(t) from TrialConsultation t
             where t.coachProfile.id = :coachId and t.status = :status
            """)
    long countByCoachAndStatus(@Param("coachId") Long coachId,
                               @Param("status") TrialConsultationStatus status);

    @Query("""
            select count(t) > 0 from TrialConsultation t
             where t.coachProfile.id = :coachId
               and t.status in :statuses
               and t.startTime < :endTime and t.endTime > :startTime
            """)
    boolean existsActiveOverlap(@Param("coachId") Long coachId,
                                @Param("statuses") Collection<TrialConsultationStatus> statuses,
                                @Param("startTime") Instant startTime,
                                @Param("endTime") Instant endTime);

    Optional<TrialConsultation> findFirstByCoachProfileIdAndStatusInAndStartTimeAfterOrderByStartTimeAsc(
            Long coachId, Collection<TrialConsultationStatus> statuses, Instant now);
}
