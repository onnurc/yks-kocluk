package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link MeetLinkProperties} (automatic meet-link generation switch). */
@Configuration
@EnableConfigurationProperties(MeetLinkProperties.class)
public class MeetLinkConfig {
}
