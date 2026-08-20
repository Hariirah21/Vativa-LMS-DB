package com.example.lms.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class PackageDto {

    @Data
    @NoArgsConstructor
    public static class Request {

        // S#10 - Available Package (Basic / Standard / Premium / Existing)
        @NotBlank(message = "Please select a package")
        @Pattern(
                regexp = "Basic|Standard|Premium|Existing",
                message = "Please select a package"
        )
        private String availablePackage;

        // S#11 - Package Name (3-100 chars, unique - uniqueness checked in service layer)
        @NotBlank(message = "Package Name is required")
        @Size(min = 3, max = 100, message = "Package Name must be at least 3 characters")
        private String name;

        // S#12 - Package Description (optional, max 500 words)
        // Word-count (not char-count) is checked via the custom validator below,
        // since Bean Validation has no built-in "max words" constraint.
        private String description;

        // S#13 - Price (mandatory, must be > 0)
        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than zero")
        private Double price;

        // S#14 - Billing Cycle (mandatory, Monthly / Yearly only)
        @NotBlank(message = "Please select a billing cycle")
        @Pattern(
                regexp = "Monthly|Yearly",
                message = "Please select a billing cycle"
        )
        private String billingCycle;

        // S#15 - User Limit (mandatory, >= 1)
        @NotNull(message = "User Limit is required")
        @Min(value = 1, message = "User Limit is required")
        private Integer userLimit;

        // S#16 - Storage Limit (optional, >= 0)
        @Min(value = 0, message = "Storage Limit must be zero or greater")
        private Integer storageLimit;

        // Admin Permission page - at least one Create/Read/Update/Delete
        // toggle must be enabled somewhere before Save Package is allowed.
        @NotNull(message = "Please configure the required permissions before saving")
        private List<Category> permissions;

        // Custom cross-field rule: SRS Field #12 caps description at 500 words.
        // Runs only when a description was actually entered (field is optional).
        @AssertTrue(message = "Package Description must not exceed 500 words")
        public boolean isDescriptionWithinWordLimit() {
            if (description == null || description.trim().isEmpty()) {
                return true;
            }
            return description.trim().split("\\s+").length <= 500;
        }

        // Custom cross-field rule: SRS Field #6 (Admin Permission) - "at least
        // one permission should be configured" before Save Package is enabled.
        @AssertTrue(message = "Please configure the required permissions before saving")
        public boolean isAtLeastOnePermissionEnabled() {
            if (permissions == null || permissions.isEmpty()) {
                return false;
            }
            return permissions.stream()
                    .filter(cat -> cat.getFeatures() != null)
                    .flatMap(cat -> cat.getFeatures().stream())
                    .filter(feature -> feature.getPermissions() != null)
                    .anyMatch(feature -> {
                        Permission p = feature.getPermissions();
                        return p.isCreate() || p.isRead() || p.isUpdate() || p.isDelete();
                    });
        }
    }

    @Data
    @NoArgsConstructor
    public static class Response {
        private Long id;
        private String availablePackage;
        private String name;
        private String description;
        private Double price;
        private String billingCycle;
        private Integer userLimit;
        private Integer storageLimit;
        private String status;
        private LocalDateTime createdAt;
        private List<Category> permissions;
    }

    // Matches screenshot: Authentication, Course Management, Content Management, Enrollment...
    @Data
    @NoArgsConstructor
    public static class Category {
        private String id;
        private String name;
        private boolean enabled;
        private List<Feature> features;
    }

    // Matches screenshot: Create Course, Course List, Organize Modules, Multimedia, Ebook...
    @Data
    @NoArgsConstructor
    public static class Feature {
        private String id;
        private String name;
        private Permission permissions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Permission {
        private boolean create;
        private boolean read;
        private boolean update;
        private boolean delete;
    }
}