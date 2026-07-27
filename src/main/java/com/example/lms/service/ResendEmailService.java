package com.example.lms.service;

import com.example.lms.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Thin wrapper around the Resend (https://resend.com) transactional email
 * API. Shared infrastructure used only by ForgotPasswordService right now
 * (to deliver the password reset link).
 *
 * IMPORTANT: `resend.api-key` in application.yml is currently a TEMPORARY
 * placeholder for local development. Swap it for the real key (env var
 * RESEND_API_KEY) once issued - no code changes needed elsewhere.
 */
@Slf4j
@Service
public class ResendEmailService {

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.base-url}")
    private String baseUrl;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Value("${resend.from-name}")
    private String fromName;

    private WebClient webClient() {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    public void sendResetLinkEmail(String toEmail, String resetLink, int expiryMinutes) {
        String subject = "Reset your LMS password";
        String html = buildResetLinkHtml(resetLink, expiryMinutes);

        Map<String, Object> payload = Map.of(
                "from", fromName + " <" + fromEmail + ">",
                "to", new String[]{toEmail},
                "subject", subject,
                "html", html
        );

        try {
            webClient().post()
                    .uri("")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.error("Resend email dispatch failed for {}: {}", toEmail, e.getMessage());
            throw new ApiException("Unable to process the request. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String buildResetLinkHtml(String resetLink, int expiryMinutes) {
        return "<div style=\"font-family:sans-serif\">"
                + "<h2>Reset your password</h2>"
                + "<p>Click the button below to reset your LMS password:</p>"
                + "<p><a href=\"" + resetLink + "\" "
                + "style=\"display:inline-block;padding:10px 20px;background:#2563eb;"
                + "color:#ffffff;text-decoration:none;border-radius:6px;\">Reset Password</a></p>"
                + "<p>Or copy and paste this link into your browser:<br>" + resetLink + "</p>"
                + "<p>This link expires in " + expiryMinutes + " minutes. "
                + "If you did not request this, you can ignore this email.</p>"
                + "</div>";
    }
}