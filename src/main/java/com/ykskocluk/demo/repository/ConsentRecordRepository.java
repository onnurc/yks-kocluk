package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link ConsentRecord}.
 */
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {
}
