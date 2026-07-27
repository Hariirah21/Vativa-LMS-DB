package com.example.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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
        private String category;

        @NotNull(message = "Instructor selection is required")
        private Long instructorId;

        @NotBlank(message = "Course Level is required")
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
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}