package com.example.lms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ebooks")
@Getter
@Setter
@NoArgsConstructor
public class Ebook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ebook_course"))
    private CourseEntity course;

    @NotBlank(message = "Section is required.")
    @Size(max = 100, message = "Section must not exceed 100 characters.")
    @Column(nullable = false, length = 100)
    private String section;

    @Size(max = 100, message = "Content Title must not exceed 100 characters.")
    @Column(name = "content_title", length = 100)
    private String contentTitle;

    @Column(name = "uploaded_file_name", length = 255)
    private String uploadedFileName;

    @Column(name = "uploaded_file_url", length = 2048)
    private String uploadedFileUrl;

    @Column(name = "uploaded_file_size")
    private Long uploadedFileSize;

    @Lob
    @Column(name = "imported_content", columnDefinition = "TEXT")
    private String importedContent;

    @Lob
    @Column(name = "own_layout", columnDefinition = "TEXT")
    private String ownLayout;

    @Lob
    @Column(name = "blank_ebook", columnDefinition = "TEXT")
    private String blankEbook;

    @Lob
    @Column(name = "course_welcome", columnDefinition = "TEXT")
    private String courseWelcome;

    @Lob
    @Column(name = "course_overview", columnDefinition = "TEXT")
    private String courseOverview;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Lob
    @Column(name = "ebook_cover", columnDefinition = "TEXT")
    private String ebookCover;

    @Lob
    @Column(name = "media_and_text", columnDefinition = "TEXT")
    private String mediaAndText;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String counters;

    @Lob
    @Column(name = "download_file", columnDefinition = "TEXT")
    private String downloadFile;

    @Lob
    @Column(name = "external_content", columnDefinition = "TEXT")
    private String externalContent;

    @Lob
    @Column(name = "team_and_authors", columnDefinition = "TEXT")
    private String teamAndAuthors;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String layout;

    @Lob
    @Column(name = "ebook_text", columnDefinition = "TEXT")
    private String ebookText;

    @Lob
    @Column(name = "section_design", columnDefinition = "TEXT")
    private String sectionDesign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id",
            foreignKey = @ForeignKey(name = "fk_ebook_creator"))
    @JsonIgnore
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id",
            foreignKey = @ForeignKey(name = "fk_ebook_updater"))
    @JsonIgnore
    private User updatedBy;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.version == null) {
            this.version = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}