package com.example.lms.service;

import com.example.lms.exception.ApiException;
import io.netty.channel.ChannelOption;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;


@Slf4j
@Service
public class ResendEmailService implements PasswordResetEmailService {

    @Value("${resend.api-key}")
    private String apiKey;

    @Value("${resend.base-url}")
    private String baseUrl;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Value("${resend.from-name}")
    private String fromName;

    @Value("${resend.timeout-ms:5000}")
    private long timeoutMs;

    @Value("${resend.max-retries:1}")
    private int maxRetries;

    private WebClient webClient;

    @PostConstruct
    void init() {
        requireNonBlank(apiKey, "resend.api-key");
        requireNonBlank(baseUrl, "resend.base-url");
        requireNonBlank(fromEmail, "resend.from-email");
        requireNonBlank(fromName, "resend.from-name");

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMs)
                .responseTimeout(Duration.ofMillis(timeoutMs));

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private void requireNonBlank(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required configuration property '" + propertyName + "' is missing or blank. " +
                            "Set it via application.yml or an environment variable before starting the app.");
        }
    }

    @Override
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
            webClient.post()
                    .uri("")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(300))
                            .filter(this::isRetryable))
                    .block(Duration.ofMillis(timeoutMs * (maxRetries + 1)));
        } catch (Exception e) {
            log.error("Resend email dispatch failed for {}: {}", maskEmail(toEmail), e.getMessage());
            throw new ApiException("Unable to process the request. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Retry on network-level failures and 5xx; never on 4xx (bad key, bad payload won't fix itself). */
    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientRequestException) {
            return true; // connection refused, DNS failure, timeout, etc.
        }
        if (throwable instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        return false;
    }

    /** j***@example.com style masking - enough for correlating log lines without exposing the full address. */
    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "[unknown]";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
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
