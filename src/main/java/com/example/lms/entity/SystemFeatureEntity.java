package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(
        name = "system_features",
        indexes = @Index(name = "idx_system_features_category", columnList = "category_id")
)
@Getter
@Setter
@NoArgsConstructor
public class SystemFeatureEntity {

    @Id
    @Column(length = 64)
    private String id; // e.g. "COURSE_MGMT_CREATE_COURSE"

    @Column(name = "category_id", nullable = false, length = 64)
    private String categoryId; // e.g. "COURSE_MGMT"

    @Column(name = "category_name", nullable = false)
    private String categoryName; // e.g. "Course Management"

    @Column(nullable = false)
    private String name; // e.g. "Create Course"

    /**
     * Lets you retire a feature from being assignable without deleting
     * history for packages that already reference it.
     */
    @Column(nullable = false)
    private boolean active = true;
}