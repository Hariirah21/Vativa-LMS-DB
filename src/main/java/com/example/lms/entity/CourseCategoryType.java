package com.example.lms.entity;

import java.util.Arrays;
import java.util.List;

public enum CourseCategoryType {
    TECHNICAL("Technical", "Technical courses"),
    SOFT_SKILLS("Soft Skills", "Soft skills courses"),
    COMPLIANCE("Compliance", "Compliance courses"),
    LEADERSHIP("Leadership", "Leadership courses");

    private final String displayName;
    private final String description;

    CourseCategoryType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static boolean supports(String name) {
        if (name == null) {
            return false;
        }
        return Arrays.stream(values())
                .anyMatch(category -> category.displayName.equals(name));
    }

    public static List<String> displayNames() {
        return Arrays.stream(values())
                .map(CourseCategoryType::getDisplayName)
                .toList();
    }
}
