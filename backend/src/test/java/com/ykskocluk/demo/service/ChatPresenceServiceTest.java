package com.ykskocluk.demo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPresenceServiceTest {

    @Test
    void firstConnectAndFinalDisconnectProduceTransitions() {
        ChatPresenceService presence = new ChatPresenceService();

        assertThat(presence.connect("session-a", 7L)).hasValueSatisfying(change -> {
            assertThat(change.userId()).isEqualTo(7L);
            assertThat(change.online()).isTrue();
        });
        assertThat(presence.isOnline(7L)).isTrue();
        assertThat(presence.disconnect("session-a")).hasValueSatisfying(change ->
                assertThat(change.online()).isFalse());
        assertThat(presence.isOnline(7L)).isFalse();
    }

    @Test
    void disconnectingOneOfTwoSessionsKeepsUserOnline() {
        ChatPresenceService presence = new ChatPresenceService();
        presence.connect("session-a", 7L);

        assertThat(presence.connect("session-b", 7L)).isEmpty();
        assertThat(presence.disconnect("session-a")).isEmpty();
        assertThat(presence.isOnline(7L)).isTrue();
        assertThat(presence.disconnect("session-b")).isPresent();
        assertThat(presence.isOnline(7L)).isFalse();
    }

    @Test
    void duplicateOrUnknownSessionDoesNotCreateFalseTransitions() {
        ChatPresenceService presence = new ChatPresenceService();
        presence.connect("session-a", 7L);

        assertThat(presence.connect("session-a", 7L)).isEmpty();
        assertThat(presence.disconnect("unknown")).isEmpty();
        assertThat(presence.isOnline(7L)).isTrue();
    }
}
