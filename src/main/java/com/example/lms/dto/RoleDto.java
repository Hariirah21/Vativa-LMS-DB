package com.example.lms.dto;

import com.example.lms.dto.PackageDto.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class RoleDto {

    public static class Request {

        @NotBlank(message = "Role Name is required")
        @Size(min = 4, max = 50, message = "Role name must be between 4 and 50 characters")
        private String name;

        private String description;

        @NotNull(message = "Please configure the required permissions")
        private List<Category> permissions;

        public Request() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<Category> getPermissions() { return permissions; }
        public void setPermissions(List<Category> permissions) { this.permissions = permissions; }
    }

    public static class Response {
        private Long id;
        private String name;
        private String description;
        private List<Category> permissions;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Response() {}

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<Category> getPermissions() { return permissions; }
        public void setPermissions(List<Category> permissions) { this.permissions = permissions; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }

    // Used for the "toggle Active/Inactive" action from the role list screen
    public static class StatusUpdateRequest {
        @NotBlank(message = "Status is required")
        private String status; // Active / Inactive

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}