package com.example.lms.service;

import com.example.lms.dto.SignUpDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.CommonPasswordChecker;
import com.example.lms.util.PhoneValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for 01_US_Sign_Up.
 *
 * Rules enforced here (beyond bean-validation on the DTO):
 *  - Email must not already be registered.
 *  - Password must not equal the Email ID.
 *  - Password must not be a commonly used password.
 *  - Confirm Password must exactly match Password.
 *  - Phone Number length must match the selected Country Code.
 *  - Role is NOT accepted from the client; every new account is stored
 *    with role = "ADMIN" (business rule, not present in the original SRS).
 *
 * Duplicate-submit protection: the existsByEmailIgnoreCase() pre-check
 * handles the common case, but the DB-level unique constraint on email
 * is the real guard against a race between two simultaneous requests
 * with the same email - see GlobalExceptionHandler for how the resulting
 * DataIntegrityViolationException is converted to the SRS error message.
 */
@Service
@RequiredArgsConstructor
public class SignUpService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignUpDto.SignUpResponse signUp(SignUpDto.SignUpRequest request) {
        try {
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                throw new ApiException("Passwords do not match.", HttpStatus.BAD_REQUEST);
            }

            if (request.getPassword().equalsIgnoreCase(request.getEmail())) {
                throw new ApiException("Password should not match Email ID.", HttpStatus.BAD_REQUEST);
            }

            if (CommonPasswordChecker.isCommon(request.getPassword())) {
                throw new ApiException("Password should not be a commonly used password.", HttpStatus.BAD_REQUEST);
            }

            // NEW - validate the code itself is one of the predefined ones (SRS Field #4)
            if (!PhoneValidationUtil.isSupportedCountryCode(request.getCountryCode())) {
                throw new ApiException("Country Code is required", HttpStatus.BAD_REQUEST);
            }

            if (!PhoneValidationUtil.isValid(request.getCountryCode(), request.getPhoneNumber())) {
                throw new ApiException(
                        "Enter a phone number with a valid length for the selected country code.",
                        HttpStatus.BAD_REQUEST);
            }

            if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
                throw new ApiException("Email ID already exists.", HttpStatus.CONFLICT);
            }

            User user = User.builder()
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .countryCode(request.getCountryCode())
                    .phoneNumber(request.getPhoneNumber())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role("ADMIN")
                    .acceptedTerms(request.getAcceptTerms())
                    .active(true)
                    .build();

            User saved;
            try {
                saved = userRepository.save(user);
            } catch (DataIntegrityViolationException e) {
                throw new ApiException("Email ID already exists.", HttpStatus.CONFLICT);
            }

            return SignUpDto.SignUpResponse.builder()
                    .id(saved.getId())
                    .firstName(saved.getFirstName())
                    .lastName(saved.getLastName())
                    .email(saved.getEmail())
                    .countryCode(saved.getCountryCode())
                    .phoneNumber(saved.getPhoneNumber())
                    .role(saved.getRole())
                    .build();

        } catch (ApiException e) {
            throw e; // known business-rule errors pass through untouched
        } catch (Exception e) {
            // SRS edge case #5 - exact required wording
            throw new ApiException("Unable to create an account. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}