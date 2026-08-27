package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Table(
        name = "packages",
        indexes = {
                @Index(name = "idx_packages_name", columnList = "name"),
                @Index(name = "idx_packages_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PackageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "available_package", nullable = false)
    private String availablePackage; // Basic / Standard / Premium / Existing

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column(name = "billing_cycle", nullable = false)
    private String billingCycle; // Monthly / Yearly

    @Column(name = "user_limit", nullable = false)
    private Integer userLimit;

    @Column(name = "storage_limit")
    private Integer storageLimit;

    @Column(nullable = false)
    private String status = "Active"; // Active / Inactive

    @Lob
    @Column(name = "permissions_json", columnDefinition = "TEXT")
    private String permissionsJson; // stores Category>Feature>Permission tree as JSON

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}