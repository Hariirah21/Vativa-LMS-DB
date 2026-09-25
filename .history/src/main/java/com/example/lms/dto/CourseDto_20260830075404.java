package com.example.lms.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.example.lms.validation.ValidCourseLevel;
import com.example.lms.validation.ValidCourseCategory;
import com.example.lms.validation.ValidCourseStatus;

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

        @NotBlank(message = "Course Status is required")
        @ValidCourseStatus
        private String status;
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
}