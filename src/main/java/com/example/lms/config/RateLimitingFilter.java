package com.example.lms.config;

import com.example.lms.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.rate-limit.auth.max-requests:10}")
    private int maxRequests;

    @Value("${app.rate-limit.auth.window-seconds:60}")
    private int windowSeconds;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(@org.springframework.lang.NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only throttle the unauthenticated auth endpoints where abuse is possible.
        return !(path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/signup")
                || path.startsWith("/api/auth/forgot-password"));
    }

    @Override
    protected void doFilterInternal(@org.springframework.lang.NonNull HttpServletRequest request,
                                     @org.springframework.lang.NonNull HttpServletResponse response,
                                     @org.springframework.lang.NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String key = clientIp(request) + ":" + request.getRequestURI();
        long nowEpochSec = Instant.now().getEpochSecond();

        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || nowEpochSec - existing.windowStartEpochSec >= windowSeconds) {
                return new Window(nowEpochSec, 1);
            }
            existing.count++;
            return existing;
        });

        if (window.count > maxRequests) {
            respondTooManyRequests(response, windowSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        // Trust X-Forwarded-For only if you terminate TLS behind a known,
        // trusted reverse proxy that always sets/overwrites this header.
        // Otherwise a client can spoof it to evade this limiter entirely.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void respondTooManyRequests(HttpServletResponse response, int retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        ApiResponse<Void> body = ApiResponse.error("Too many requests. Please try again later.");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private static final class Window {
        final long windowStartEpochSec;
        volatile int count;

        Window(long windowStartEpochSec, int count) {
            this.windowStartEpochSec = windowStartEpochSec;
            this.count = count;
        }
    }
}
