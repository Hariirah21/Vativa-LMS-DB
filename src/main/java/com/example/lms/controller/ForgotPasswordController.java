package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.ForgotPasswordDto;
import com.example.lms.service.ForgotPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth/forgot-password")
@RequiredArgsConstructor
public class ForgotPasswordController {

    private final ForgotPasswordService forgotPasswordService;

    @PostMapping("/send-reset-link")
    public ResponseEntity<ApiResponse<Void>> sendResetLink(
            @Valid @RequestBody ForgotPasswordDto.SendResetLinkRequest request) {
        forgotPasswordService.sendResetLink(request);
        return ResponseEntity.ok(ApiResponse.success("A password reset link has been sent to your registered Email ID."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ForgotPasswordDto.ResetPasswordRequest request) {
        forgotPasswordService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully."));
    }
}
