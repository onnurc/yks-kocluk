package com.ykskocluk.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "package_settings")
@Getter
@Setter
@NoArgsConstructor
public class PackageSettings extends BaseEntity {
    @Column(name = "yks_exam_year")
    private Integer yksExamYear;

    @Column(name = "yks_exam_date")
    private LocalDate yksExamDate;

    @Column(nullable = false)
    private boolean active;
}
