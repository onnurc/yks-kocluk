package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.MarketingChannel;
import com.ykskocluk.demo.enums.MarketingPreferenceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "marketing_preferences")
@Getter @Setter @NoArgsConstructor
public class MarketingPreference extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketingChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketingPreferenceStatus status;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(nullable = false, length = 50)
    private String source;
}
