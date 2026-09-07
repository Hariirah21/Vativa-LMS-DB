package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ebooks", uniqueConstraints = @UniqueConstraint(name = "uk_ebook_client_request", columnNames = "client_request_id"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EbookEntity {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, length = 100) private String title;
    @Column(length = 500) private String description;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EbookStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id", nullable = false) private CourseEntity course;
    @Column(name = "created_by", nullable = false, length = 255) private String createdBy;
    @Column(name = "client_request_id") private UUID clientRequestId;
    @Version @Column(nullable = false) private Long version;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "published_at") private LocalDateTime publishedAt;
    @OneToMany(mappedBy = "ebook", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default private List<EbookMediaEntity> media = new ArrayList<>();

    @PrePersist void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) status = EbookStatus.DRAFT;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
