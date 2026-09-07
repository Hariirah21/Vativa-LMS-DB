package com.example.lms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ebook_media")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EbookMediaEntity {
    @Id @GeneratedValue private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ebook_id", nullable = false) private EbookEntity ebook;
    @Column(name = "file_path", nullable = false, length = 1000) private String filePath;
    @Column(name = "original_file_name", nullable = false, length = 255) private String originalFileName;
    @Column(name = "content_type", nullable = false, length = 150) private String contentType;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
