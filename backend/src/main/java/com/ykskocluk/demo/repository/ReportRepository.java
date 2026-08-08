package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.Report;
import com.ykskocluk.demo.enums.ReportStatus;
import com.ykskocluk.demo.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;

/**
 * Repository for {@link Report}.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    @Query("""
            select count(r) > 0 from Report r
             where r.reporter.id = :reporterId
               and r.targetType = :targetType
               and r.targetId = :targetId
               and r.status in (com.ykskocluk.demo.enums.ReportStatus.OPEN,
                                com.ykskocluk.demo.enums.ReportStatus.REVIEWED)
            """)
    boolean existsOpenReport(@Param("reporterId") Long reporterId,
                             @Param("targetType") ReportTargetType targetType,
                             @Param("targetId") Long targetId);

    long countByStatusIn(Collection<ReportStatus> statuses);
}
