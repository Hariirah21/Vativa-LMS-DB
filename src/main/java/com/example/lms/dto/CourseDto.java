package com.example.lms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.example.lms.validation.CourseValidators.ValidCourseCategory;
import com.example.lms.validation.CourseValidators.ValidCourseLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class CourseDto {

    // Create / Update Course form body
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CourseRequest {

        @NotBlank(message = "Course Name is required.")
        private String name;

        @NotBlank(message = "Course Category is required")
        @ValidCourseCategory
        private String category;

        @NotNull(message = "Instructor selection is required")
        private Long instructorId;

        @NotBlank(message = "Course Level is required")
        @ValidCourseLevel
        private String courseLevel;

        // Optional per SRS - max 1000 characters
        @Size(max = 1000, message = "Course Description must not exceed 1000 characters")
        private String description;

        private String thumbnailUrl;
    }

    // Course List / Course Detail response
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CourseResponse {
        private Long id;
        private String name;
        private String category;
        private Long instructorId;
        private String courseLevel;
        private String description;
        private String thumbnailUrl;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InstructorOption {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
    }

    // Enrolled Course List response for a selected course
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrolledCourseResponse {
        private Long enrollmentId;
        private Long userId;
        private LocalDate enrollmentDate;
        private LocalDate completionDate;
        private LocalDate expirationDate;
        private BigDecimal progressPercentage;
        private String status;
        private BigDecimal scorePercentage;
        private Boolean completion;
    }

    // Enroll User form: selected course plus mandatory multi-selected users
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollUsersRequest {
        @NotNull(message = "Course selection is required")
        private Long courseId;

        @NotBlank(message = "Client request ID is required")
        private String clientRequestId;

        @NotNull(message = "User is required.")
        @Size(min = 1, message = "Please select at least one user.")
        private List<@NotNull(message = "User is required.") Long> userIds;
    }

    // Enrolled Course List checkbox selection for the bulk Unenroll action
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UnenrollUsersRequest {
        @NotNull(message = "Course selection is required")
        private Long courseId;

        @NotEmpty(message = "At least one enrollment must be selected")
        private List<@NotNull(message = "Selected enrollment ID is required") Long> enrollmentIds;
    }
}
