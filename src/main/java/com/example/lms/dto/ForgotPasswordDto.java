package com.example.lms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payloads for the Forgot Password feature (03__US_Forgot_Password),
 * implemented per the SRS as an email reset-LINK flow (no OTP).
 *
 * Flow:
 *   1) POST /send-reset-link -> emails a password reset link to the
 *      registered address (SRS step 4-5)
 *   2) User clicks the link -> frontend Reset Password page reads the
 *      "token" query param from the URL (SRS step 6-7)
 *   3) POST /reset-password  -> submits token + new password (SRS step 8-9).
 *      The token alone identifies the account; email is not required here.
 */
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

        @NotBlank(message = "Confirm Password is required.")
        private String confirmPassword;
    }
}