package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request / Response payloads for the Login feature (02__US_Login).
 */
public class LoginDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginRequest {

        @NotBlank(message = "Email ID is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Enter a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 16, message = "Invalid Email ID or Password")
        private String password;

        // Optional field from the Field List - not mandatory.
        // When true, LoginService issues a longer-lived JWT (see JwtUtil).
        private Boolean rememberMe;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoginResponse {
        private String token;
        private String tokenType;
        private Long userId;
        private String firstName;
        private String lastName;
        private String email;
        private String role;
    }
}