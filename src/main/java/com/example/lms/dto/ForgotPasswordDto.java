package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class ForgotPasswordDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SendResetLinkRequest {
        @NotBlank(message = "Registered Email ID is required.")
        @Email(message = "Enter a valid Email ID.")
        @Size(max = 254, message = "Enter a valid Email ID.")
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResetPasswordRequest {
        @NotBlank(message = "Reset link is invalid or expired.")
        private String token;

        @NotBlank(message = "New Password is required.")
        @Size(min = 8, max = 20, message = "Passwords must be between 8 and 20 characters.")
        private String newPassword;

        @NotBlank(message = "Confirm Password are required.")
        private String confirmPassword;
    }
}
