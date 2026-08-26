package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.MediaAsset;
import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaVisibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    Optional<MediaAsset> findByPublicTokenAndStatusAndVisibility(
            String publicToken, MediaStatus status, MediaVisibility visibility);

    @Query("select asset.id from MediaAsset asset where asset.status = :status "
            + "and asset.createdAt < :cutoff order by asset.createdAt asc")
    List<Long> findStaleIds(@Param("status") MediaStatus status,
                            @Param("cutoff") Instant cutoff,
                            Pageable pageable);
}
