package com.example.lms.service;


public interface PasswordResetEmailService {
    void sendResetLinkEmail(String toEmail, String resetLink, int expiryMinutes);
}
