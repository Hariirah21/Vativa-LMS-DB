package com.example.lms.entity;

import com.example.lms.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum LearnerReportFilter {
    COMPLETED("Completed Courses"),
    INCOMPLETED("Incompleted Courses"),
    ENROLLED("Enrolled Courses");

    private final String apiValue;

    LearnerReportFilter(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }

    public static LearnerReportFilter from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(filter -> filter.apiValue.equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        "Filter must be Completed Courses, Incompleted Courses, or Enrolled Courses.",
                        HttpStatus.BAD_REQUEST));
    }
}
