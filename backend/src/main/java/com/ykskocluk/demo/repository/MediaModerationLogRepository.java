package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.MediaModerationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaModerationLogRepository extends JpaRepository<MediaModerationLog, Long> {
}
