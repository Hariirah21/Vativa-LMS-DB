package com.example.lms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public class PackageDto {

    public static class Request {

        @NotBlank(message = "Package Name is required")
        private String name;

        private String description;

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be greater than zero")
        private Double price;

        @NotBlank(message = "Please select a billing cycle")
        private String billingCycle;

        @NotNull(message = "User Limit is required")
        private Integer userLimit;

        private Integer storageLimit;

        @NotNull(message = "Please configure the required permissions")
        private List<Category> permissions;

        public Request() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }

        public String getBillingCycle() { return billingCycle; }
        public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }

        public Integer getUserLimit() { return userLimit; }
        public void setUserLimit(Integer userLimit) { this.userLimit = userLimit; }

        public Integer getStorageLimit() { return storageLimit; }
        public void setStorageLimit(Integer storageLimit) { this.storageLimit = storageLimit; }

        public List<Category> getPermissions() { return permissions; }
        public void setPermissions(List<Category> permissions) { this.permissions = permissions; }
    }

    public static class Response {
        private Long id;
        private String name;
        private String description;
        private Double price;
        private String billingCycle;
        private Integer userLimit;
        private Integer storageLimit;
        private String status;
        private LocalDateTime createdAt;
        private List<Category> permissions;

        public Response() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }

        public String getBillingCycle() { return billingCycle; }
        public void setBillingCycle(String billingCycle) { this.billingCycle = billingCycle; }

        public Integer getUserLimit() { return userLimit; }
        public void setUserLimit(Integer userLimit) { this.userLimit = userLimit; }

        public Integer getStorageLimit() { return storageLimit; }
        public void setStorageLimit(Integer storageLimit) { this.storageLimit = storageLimit; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public List<Category> getPermissions() { return permissions; }
        public void setPermissions(List<Category> permissions) { this.permissions = permissions; }
    }

    // Matches screenshot: Authentication, Course Management, Content Management, Enrollment...
    public static class Category {
        private String id;
        private String name;
        private boolean enabled;
        private List<Feature> features;

        public Category() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<Feature> getFeatures() { return features; }
        public void setFeatures(List<Feature> features) { this.features = features; }
    }

    // Matches screenshot: Create Course, Course List, Organize Modules, Multimedia, Ebook...
    public static class Feature {
        private String id;
        private String name;
        private Permission permissions;

        public Feature() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Permission getPermissions() { return permissions; }
        public void setPermissions(Permission permissions) { this.permissions = permissions; }
    }

    public static class Permission {
        private boolean create;
        private boolean read;
        private boolean update;
        private boolean delete;

        public Permission() {}
        public Permission(boolean create, boolean read, boolean update, boolean delete) {
            this.create = create; this.read = read; this.update = update; this.delete = delete;
        }

        public boolean isCreate() { return create; }
        public void setCreate(boolean create) { this.create = create; }
        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }
        public boolean isUpdate() { return update; }
        public void setUpdate(boolean update) { this.update = update; }
        public boolean isDelete() { return delete; }
        public void setDelete(boolean delete) { this.delete = delete; }
    }
}