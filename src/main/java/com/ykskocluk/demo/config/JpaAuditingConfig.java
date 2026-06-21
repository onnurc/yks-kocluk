package com.ykskocluk.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing so {@code @CreatedDate} / {@code @LastModifiedDate}
 * on {@code BaseEntity} are populated automatically.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
