package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link SessionReminderProperties} (reminder lead time). */
@Configuration
@EnableConfigurationProperties(SessionReminderProperties.class)
public class SessionReminderConfig {
}
