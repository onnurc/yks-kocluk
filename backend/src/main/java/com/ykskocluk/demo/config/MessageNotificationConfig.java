package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link MessageNotificationProperties} (new-message email debounce + link base). */
@Configuration
@EnableConfigurationProperties(MessageNotificationProperties.class)
public class MessageNotificationConfig {
}
