package com.ykskocluk.demo.repository;

import com.ykskocluk.demo.entity.AdminConversationAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link AdminConversationAccessLog}.
 */
public interface AdminConversationAccessLogRepository
        extends JpaRepository<AdminConversationAccessLog, Long> {
}
