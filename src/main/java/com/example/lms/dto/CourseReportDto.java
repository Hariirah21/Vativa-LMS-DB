package com.example.lms.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

public final class CourseReportDto {
    private CourseReportDto() {
    }

    @Getter
    @Setter
    public static class FilterRequest {
        @Size(max = 100, message = "Search must not exceed 100 characters.")
        private String search;

        @Size(max = 100, message = "Category must not exceed 100 characters.")
        private String category;

        @Positive(message = "Category ID must be a positive number.")
        private Long categoryId;

        @PositiveOrZero(message = "Enrollments must be zero or greater.")
        private Long enrollments;

        @PositiveOrZero(message = "Minimum enrollments must be zero or greater.")
        private Long minEnrollments;

        @PositiveOrZero(message = "Maximum enrollments must be zero or greater.")
        private Long maxEnrollments;

        @DecimalMin(value = "0.00", message = "Completion rate must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Completion rate must be between 0 and 100.")
        private BigDecimal completionRate;

        @DecimalMin(value = "0.00", message = "Minimum completion rate must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Minimum completion rate must be between 0 and 100.")
        private BigDecimal minCompletionRate;

        @DecimalMin(value = "0.00", message = "Maximum completion rate must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Maximum completion rate must be between 0 and 100.")
        private BigDecimal maxCompletionRate;

        @PositiveOrZero(message = "Completed learners must be zero or greater.")
        private Long completedLearners;

        @PositiveOrZero(message = "Minimum completed learners must be zero or greater.")
        private Long minCompletedLearners;

        @PositiveOrZero(message = "Maximum completed learners must be zero or greater.")
        private Long maxCompletedLearners;

        @PositiveOrZero(message = "Learners not started must be zero or greater.")
        private Long learnersNotStarted;

        @PositiveOrZero(message = "Minimum learners not started must be zero or greater.")
        private Long minLearnersNotStarted;

        @PositiveOrZero(message = "Maximum learners not started must be zero or greater.")
        private Long maxLearnersNotStarted;
    }

    @Getter
    @Builder
    public static class ListResponse {
        private Long courseId;
        private String courseTitle;
        private String courseCode;
        private String category;
        private long enrollments;
        private BigDecimal completionRate;
        private long completedLearners;
        private long learnersNotStarted;
    }

    @Getter
    @Builder
    public static class OverviewResponse {
        private Long courseId;
        private String courseTitle;
        private String courseCode;
        private String category;
        private long enrollments;
        private long learners;
        private BigDecimal completionRate;
        private long completedLearners;
        private long learnersInProgress;
        private long learnersNotStarted;
        private BigDecimal averageUserProgress;
        private Long totalTimeSpent;
        private Long certificatesIssued;
    }
}
