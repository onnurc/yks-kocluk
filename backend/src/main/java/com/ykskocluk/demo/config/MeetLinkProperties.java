package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings bound from {@code app.meet-link.*}. {@code enabled} is the master switch for
 * automatic meet-link generation. OFF (the default) means the platform mints no link at all —
 * the coach creates a Google Meet link and shares it over chat. The seam around it
 * ({@code MeetClient}, {@code sessions.meet_link}, {@code SessionResponse.meetLink}) is kept
 * deliberately, so a future Google Meet integration is a client implementation plus a flag
 * flip rather than a schema/DTO change.
 */
@ConfigurationProperties(prefix = "app.meet-link")
public record MeetLinkProperties(boolean enabled) {
}
