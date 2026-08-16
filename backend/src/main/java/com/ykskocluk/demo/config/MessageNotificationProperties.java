package com.ykskocluk.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings bound from {@code app.message-notification.*} — the "you have a new message" email.
 *
 * @param debounce how long after one such email the same recipient is left alone about the same
 *        conversation. This is the only thing standing between a chat feature and an inbox full of
 *        mail, so it is a config knob rather than a constant. Enforced as an atomic claim on
 *        {@code conversations.student_notified_at} / {@code coach_notified_at}, per recipient.
 * @param frontendBaseUrl origin the deep link is built against. Points at the same
 *        {@code FRONTEND_BASE_URL} env var as {@code app.password-security.frontend-base-url}
 *        rather than reusing that property — password recovery and chat notifications have no
 *        business sharing a config binding just because the value happens to match today.
 */
@ConfigurationProperties(prefix = "app.message-notification")
public record MessageNotificationProperties(
        Duration debounce,
        String frontendBaseUrl
) {

    /** Deep link to the conversation — the mail's only payload beyond the sender's name. */
    public String conversationLink(Long conversationId) {
        return frontendBaseUrl + "/messages/" + conversationId;
    }
}
