package com.example.lms.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public final class CourseEnrollmentDto {
    private CourseEnrollmentDto() {}

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class BulkUserRequest {
        @NotEmpty(message = "Please select at least one user")
        private List<@NotNull(message = "User ID is required.") Long> userIds;
    }

    @Getter @Builder
    public static class EnrolledUserResponse {
        private Long enrollmentId;
        private Long userId;
        private String user;
        private String email;
        private LocalDateTime enrollmentDate;
        private LocalDateTime completionDate;
        private LocalDateTime expirationDate;
        private Integer progressPercent;
        private String status;
        private Integer scorePercent;
        private String completion;
    }

    @Getter @Builder
    public static class EligibleUserResponse {
        private Long userId;
        private String user;
        private String email;
    }

    @Getter @Builder
    public static class BulkOperationResponse {
        private Long courseId;
        private List<Long> userIds;
        private int affectedCount;
    }
}
