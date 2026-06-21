package com.ykskocluk.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "universities")
@Getter
@Setter
@NoArgsConstructor
public class University extends BaseEntity {

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(length = 100)
    private String city;
}
