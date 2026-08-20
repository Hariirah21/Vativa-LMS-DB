package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "permissions_json", columnDefinition = "TEXT")
    private String permissionsJson;

    // Audit only — who created the role. Roles themselves are system-wide
    // (SRS: "Role names must be unique in the system"; the Available Roles
    // dropdown lists ALL active roles, not just the current admin's roles),
    // so this is NOT used to scope or restrict queries.
    @Column(name = "created_by_admin_id", nullable = false)
    private Long createdByAdminId;

    @Column(nullable = false)
    private String status = "Active"; // Active / Inactive — SRS: defaults to Active on creation

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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