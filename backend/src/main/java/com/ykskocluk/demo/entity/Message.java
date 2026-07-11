package com.ykskocluk.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single chat message in a {@link Conversation}. {@code createdAt} (BaseEntity) is the
 * sent time; {@code readAt} is the read receipt (null until the other party reads it).
 * Messages are retained forever (CLAUDE.md: never hard-deleted). Attachments are closed in
 * beta — content is text-only.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "read_at")
    private Instant readAt;
}
