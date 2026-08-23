package com.ykskocluk.demo.entity;

import com.ykskocluk.demo.enums.ExamSession;
import com.ykskocluk.demo.enums.Track;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student's profile. One per student {@link User}. Deliberately minimal for the MVP;
 * date_of_birth / KVKK fields arrive in Phase 9.
 */
@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@NoArgsConstructor
public class StudentProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "grade_level", length = 30)
    private String gradeLevel;

    @Column(length = 100)
    private String city;

    @Column(name = "exam_year")
    private Integer examYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "yks_score_type", length = 20)
    private Track yksScoreType;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_session", length = 10)
    private ExamSession examSession;

    @Column(name = "target_university", length = 200)
    private String targetUniversity;

    @Column(name = "target_department", length = 150)
    private String targetDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_image_asset_id")
    private MediaAsset profileImageAsset;
}
