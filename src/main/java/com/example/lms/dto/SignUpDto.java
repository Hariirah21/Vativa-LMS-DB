package com.example.lms.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request / Response payloads for the Sign Up feature (01_US_Sign_Up).
 * Kept as a single file with nested static classes so the "one file per
 * module" rule still holds while both request and response live together.
 */
public class SignUpDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SignUpRequest {

        @NotBlank(message = "First Name is required.")
        @Pattern(regexp = "^[A-Za-z]{2,50}$", message = "First Name must contain only letters.")
        private String firstName;

        @NotBlank(message = "Last Name is required.")
        @Pattern(regexp = "^[A-Za-z]{1,50}$", message = "Last Name must contain only letters.")
        private String lastName;

        @NotBlank(message = "Email ID is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 100, message = "Enter a valid email address.")
        private String email;

     // SRS Field #4 - mandatory. Must be one of the predefined country codes -
     // that check happens in SignUpService against PhoneValidationUtil's list,
     // not here. This annotation only guards against a blank/missing value.
     @NotBlank(message = "Country Code is required")
     private String countryCode;

        // SRS Field #5 - numeric only, WITHOUT the country code.
        // Exact length is validated against the selected Country Code in
        // SignUpService (dependency), this annotation only enforces "digits only".
        @NotBlank(message = "Enter a valid phone number.")
        @Pattern(regexp = "^[0-9]{4,14}$", message = "Enter a valid phone number.")
        private String phoneNumber;

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 16, message = "Passwords must be between 8 and 16 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                message = "Passwords must include uppercase, lowercase, number, and special character."
        )
        private String password;

        @NotBlank(message = "Confirm Password is required.")
        private String confirmPassword;

        @NotNull(message = "Please accept the Terms & Conditions.")
        @AssertTrue(message = "Please accept the Terms & Conditions.")
        private Boolean acceptTerms;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SignUpResponse {
        private Long id;
        private String firstName;
        private String lastName;
        private String email;
        private String countryCode;
        private String phoneNumber;
        private String role;
    }
}