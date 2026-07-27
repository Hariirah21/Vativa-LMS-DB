package com.example.lms.service;

import com.example.lms.dto.ForgotPasswordDto;
import com.example.lms.entity.PasswordResetToken;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.PasswordResetTokenRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Business logic for 03__US_Forgot_Password, matching the SRS reset-link
 * flow (no OTP):
 *
 *  1) sendResetLink  - validates the email is registered, generates a
 *                       random token, stores its hash with an expiry,
 *                       emails a reset link containing the raw token.
 *  2) resetPassword  - looks the token up by its hash, checks it is
 *                       neither expired nor already used, updates the
 *                       password, and marks the token used (single-use).
 *
 * Note: unlike the OTP version, there is no separate "verify" endpoint -
 * clicking the emailed link and landing on the Reset Password page IS the
 * verification step (SRS step 6-7). Verification happens implicitly at
 * resetPassword() time by validating the token.
 */
@Service
@RequiredArgsConstructor
public class ForgotPasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResendEmailService resendEmailService;

    @Value("${reset-link.expiry-minutes}")
    private int expiryMinutes;

    @Value("${reset-link.base-url}")
    private String resetLinkBaseUrl;

    @Transactional
    public void sendResetLink(ForgotPasswordDto.SendResetLinkRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new ApiException("Email ID is not registered.", HttpStatus.NOT_FOUND));

        // Invalidate any previous reset link for this email before issuing a new one
        tokenRepository.deleteByEmailIgnoreCase(user.getEmail());

        String rawToken = TokenGenerator.generateRawToken();
        String tokenHash = TokenGenerator.hash(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(user.getEmail())
                .tokenHash(tokenHash)
                .expiryTime(LocalDateTime.now().plusMinutes(expiryMinutes))
                .used(false)
                .build();

        tokenRepository.save(resetToken);

        String resetLink = resetLinkBaseUrl + "?token=" + rawToken;
        resendEmailService.sendResetLinkEmail(user.getEmail(), resetLink, expiryMinutes);
    }

    @Transactional
    public void resetPassword(ForgotPasswordDto.ResetPasswordRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApiException("Passwords do not match.", HttpStatus.BAD_REQUEST);
        }

        String tokenHash = TokenGenerator.hash(request.getToken());

        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(
                        "The password reset link is invalid or expired. Please request a new one.",
                        HttpStatus.BAD_REQUEST));

        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            throw new ApiException(
                    "This password reset link has already been used. Please request a new one.",
                    HttpStatus.GONE);
        }

        if (resetToken.isExpired()) {
            throw new ApiException(
                    "The password reset link has expired. Please request a new one.",
                    HttpStatus.GONE);
        }

        User user = userRepository.findByEmailIgnoreCase(resetToken.getEmail())
                .orElseThrow(() -> new ApiException("Email ID is not registered.", HttpStatus.NOT_FOUND));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Single-use: mark consumed rather than delete, so a replayed request
        // (e.g. double-click on Reset Password button) is rejected as "already used"
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }
}