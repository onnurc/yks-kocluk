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

@Entity
@Table(name = "media_moderation_logs")
@Getter
@Setter
@NoArgsConstructor
public class MediaModerationLog extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User admin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_asset_id", nullable = false)
    private MediaAsset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_owner_user_id")
    private User targetOwner;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "moderated_at", nullable = false)
    private Instant moderatedAt;
}
