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
 * Access log recording when and why an admin inspected a conversation.
 */
@Entity
@Table(name = "admin_conversation_access_logs")
@Getter
@Setter
@NoArgsConstructor
public class AdminConversationAccessLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User admin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;
}
