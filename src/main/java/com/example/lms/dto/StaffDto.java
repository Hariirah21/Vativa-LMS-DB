package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class StaffDto {

    private StaffDto() {}

    public static class Request {
        @NotBlank(message = "Username is required.")
        @Size(min = 2, max = 100, message = "Username must contain between 2 and 100 characters.")
        private String username;

        @NotBlank(message = "Email ID is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email must not exceed 254 characters.")
        private String email;

        @NotBlank(message = "Role is required.")
        @Size(min = 4, max = 50, message = "Role must contain between 4 and 50 characters.")
        private String role;

        @NotNull(message = "Please configure the required permissions.")
        private List<PackageDto.Category> permissions;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public List<PackageDto.Category> getPermissions() { return permissions; }
        public void setPermissions(List<PackageDto.Category> permissions) { this.permissions = permissions; }
    }

    public static class InvitationRequest {
        @NotBlank(message = "Email ID is required.")
        @Email(message = "Enter a valid email address.")
        private String email;

        @NotBlank(message = "Category is required.")
        private String category;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }

    public static class Response {
        private Long id;
        private String username;
        private String email;
        private String role;
        private String status;
        private List<PackageDto.Category> permissions;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<PackageDto.Category> getPermissions() { return permissions; }
        public void setPermissions(List<PackageDto.Category> permissions) { this.permissions = permissions; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}
