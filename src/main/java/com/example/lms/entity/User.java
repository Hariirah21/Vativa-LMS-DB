package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "country_code", nullable = false, length = 5)
    private String countryCode;

    @Column(name = "phone_number", nullable = false, length = 16)
    private String phoneNumber;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private String role = "ADMIN";

    @Column(name = "accepted_terms", nullable = false)
    private Boolean acceptedTerms;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    
    @Column(name = "package_id")
    private Long packageId;

    
    @Column(name = "package_assigned_at")
    private LocalDateTime packageAssignedAt;

    @Column(name = "package_expires_at")
    private LocalDateTime packageExpiresAt;

    
    @Column(name = "failed_login_attempts", nullable = false,
            columnDefinition = "integer not null default 0")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    // NEW - set to "now + lockout duration" once failedLoginAttempts hits
    // the configured threshold; cleared on successful login. Null = not locked.
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.role == null) {
            this.role = "ADMIN";
        }
        if (this.failedLoginAttempts == null) {
            this.failedLoginAttempts = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Transient
    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    // NEW - convenience check used by permission-gating logic: a package
    // with no expiry (packageExpiresAt == null) never counts as expired;
    // otherwise it's expired once "now" passes that timestamp.
    @Transient
    public boolean isPackageExpired() {
        return packageExpiresAt != null && LocalDateTime.now().isAfter(packageExpiresAt);
    }
}