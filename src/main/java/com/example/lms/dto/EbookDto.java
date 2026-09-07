package com.example.lms.dto;

import com.example.lms.entity.EbookStatus;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class EbookDto {
    private EbookDto() {}

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Ebook Title is required.")
        @Size(min = 4, max = 100, message = "Ebook Title must be between 4 and 100 characters.")
        private String title;
        @Size(max = 500, message = "Description exceeds maximum length.") private String description;
        @NotBlank(message = "Ebook Content is required.") private String content;
        @NotNull(message = "Course ID is required.") private Long courseId;
        private UUID clientRequestId;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRequest {
        @NotBlank(message = "Ebook Title is required.")
        @Size(min = 4, max = 100, message = "Ebook Title must be between 4 and 100 characters.")
        private String title;
        @Size(max = 500, message = "Description exceeds maximum length.") private String description;
        @NotBlank(message = "Ebook Content is required.") private String content;
        @NotNull(message = "Version is required.") private Long version;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PublishRequest {
        @NotNull(message = "Version is required.") private Long version;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private UUID id;
        private String title;
        private String description;
        private String content;
        private EbookStatus status;
        private Long courseId;
        private String createdBy;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime publishedAt;
        private List<MediaResponse> media;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MediaResponse {
        private UUID id;
        private String fileName;
        private String contentType;
        private long size;
        private String downloadUrl;
    }
}
