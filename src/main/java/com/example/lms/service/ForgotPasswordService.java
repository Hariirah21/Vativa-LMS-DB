package com.example.lms.service;

import com.example.lms.dto.ForgotPasswordDto;
import com.example.lms.entity.PasswordResetToken;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.PasswordResetTokenRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.CommonPasswordChecker;
import com.example.lms.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class ForgotPasswordService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    // CHANGED: was the concrete ResendEmailService - now the narrow
    // PasswordResetEmailService interface, so this class doesn't depend on
    // a specific email provider (Dependency Inversion) and is easier to
    // unit-test with a mock.
    private final PasswordResetEmailService passwordResetEmailService;

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
        passwordResetEmailService.sendResetLinkEmail(user.getEmail(), resetLink, expiryMinutes);
    }

    @Transactional
    public void resetPassword(ForgotPasswordDto.ResetPasswordRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new ApiException("Passwords do not match.", HttpStatus.BAD_REQUEST);
        }

        // Same password policy as Sign Up: don't allow resetting into a
        // commonly used password.
        if (CommonPasswordChecker.isCommon(request.getNewPassword())) {
            throw new ApiException("Password should not be a commonly used password.", HttpStatus.BAD_REQUEST);
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
