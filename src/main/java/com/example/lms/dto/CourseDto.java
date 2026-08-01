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
        @Size(max = 255, message = "Course Name must not exceed 255 characters.")
        private String name;

        @NotNull(message = "Course Category is required")
        private Long categoryId;

        @NotNull(message = "Instructor selection is required")
        private Long instructorId;

        @NotBlank(message = "Course Level is required")
        private String level;

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
        private Long categoryId;
        private String categoryName;
        private String categoryDescription;
        private Boolean categoryActive;
        private Long instructorId;
        private String instructorName;
        private String instructorEmail;
        private String level;
        private String description;
        private String thumbnailUrl;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
