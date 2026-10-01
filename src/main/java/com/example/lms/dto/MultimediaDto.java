package com.example.lms.dto;

import com.example.lms.entity.MultimediaEntity.ValidResourceName;
import com.example.lms.entity.MultimediaResourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class MultimediaDto {

    private MultimediaDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadRequest {

        @ValidResourceName
        private String resourceName;

        @Size(max = 500, message = "Resource description must not exceed 500 characters.")
        private String resourceDescription;

        @NotNull(message = "Resource type is required.")
        private MultimediaResourceType resourceType;

        @NotNull(message = "Course ID is required.")
        private Long courseId;

        @NotNull(message = "File is required.")
        private MultipartFile file;

        private boolean published;

        // Reuse this UUID when retrying a request after a timeout/lost connection.
        private UUID clientRequestId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceResponse {
        private UUID id;
        private String resourceName;
        private String resourceDescription;
        private MultimediaResourceType resourceType;
        private Long courseId;
        private LocalDateTime createdAt;
        private String uploadedBy;
        private boolean published;
        private PreviewInfo preview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewInfo {
        private String fileName;
        private String contentType;
        private long size;
        private boolean inlinePreviewSupported;
        private String downloadUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadConfiguration {
        private List<String> supportedFileTypes;
        private long maximumUploadSizeBytes;
        private long maximumUploadSizeMegabytes;
    }
}
