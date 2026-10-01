package com.example.lms.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class LearnerReportDto {
    private LearnerReportDto() {
    }

    @Getter
    @Builder
    public static class ListResponse {
        private Long learnerId;
        private String learnerName;
        private String role;
        private String department;
    }

    @Getter
    @Builder
    public static class PreviewResponse {
        private Long learnerId;
        private String learnerName;
        private String role;
        private String department;
        private LocalDateTime registrationDate;
        private LocalDateTime lastActivity;
        private LocalDateTime lastLogin;
        private long completedCourses;
        private long incompleteCourses;
        private long enrolledCourses;
        private long assignedCourses;
        private BigDecimal averageScore;
        private Long studyTime;
        private Long totalTimeOnPlatform;
    }
}
