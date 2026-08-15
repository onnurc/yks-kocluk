package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings bound from {@code app.session-reminder.*}. {@code leadTime} is how far before a
 * session's start the reminder becomes eligible to send — the job's poll interval (see the
 * {@code @Scheduled} cron on {@code SessionReminderJob}) determines the actual jitter around it.
 */
@ConfigurationProperties(prefix = "app.session-reminder")
public record SessionReminderProperties(
        Duration leadTime
) {
}
