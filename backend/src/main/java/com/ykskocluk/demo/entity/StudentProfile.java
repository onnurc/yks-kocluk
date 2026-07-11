package com.ykskocluk.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
}
