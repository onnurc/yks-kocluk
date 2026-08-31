package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.MediaStatus;
import com.ykskocluk.demo.enums.MediaType;
import com.ykskocluk.demo.enums.MediaVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "media_assets")
@Getter @Setter @NoArgsConstructor
public class MediaAsset extends BaseEntity {
    /**
     * Opaque locator used only by the anonymous public-media redirect. Internal ownership and
     * lifecycle operations continue to use the database id.
     */
    @Column(name = "public_token", nullable = false, unique = true, length = 64, updatable = false)
    private String publicToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING) @Column(name = "media_type", nullable = false, length = 40)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private MediaVisibility visibility;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private MediaStatus status;

    @Column(name = "storage_deleted_at")
    private Instant storageDeletedAt;
}
