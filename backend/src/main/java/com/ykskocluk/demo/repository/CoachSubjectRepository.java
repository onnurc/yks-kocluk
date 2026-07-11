package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.CoachSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CoachSubjectRepository extends JpaRepository<CoachSubject, Long> {

    List<CoachSubject> findByCoachProfileId(Long coachProfileId);

    /** Batch-load tracks for a page of coaches (avoids N+1 in search). */
    List<CoachSubject> findByCoachProfileIdIn(Collection<Long> coachProfileIds);

    /** Bulk delete (executes immediately) so re-inserting the same tracks won't hit the unique constraint. */
    @Modifying
    @Query("delete from CoachSubject cs where cs.coachProfile.id = :coachProfileId")
    void deleteByCoachProfileId(@Param("coachProfileId") Long coachProfileId);
}
