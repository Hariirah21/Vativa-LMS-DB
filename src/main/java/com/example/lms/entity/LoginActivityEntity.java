package com.example.lms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_activities", indexes = {
        @Index(name = "idx_login_activities_logged_in_at", columnList = "logged_in_at"),
        @Index(name = "idx_login_activities_expires_at", columnList = "expires_at"),
        @Index(name = "idx_login_activities_user_id", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "logged_in_at", nullable = false, updatable = false)
    private LocalDateTime loggedInAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    void onCreate() {
        if (loggedInAt == null) {
            loggedInAt = LocalDateTime.now();
        }
    }
}
