package com.example.lms.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;


@Component
public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32; // 256-bit minimum for HS256

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // Longer-lived token issued when the user checks "Remember Me" on Login.
    @Value("${app.jwt.remember-me-expiration-ms:604800000}") // default 7 days
    private long rememberMeExpirationMs;

    private Key signingKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES +
                            " bytes (256 bits) for HS256. Configured secret is only " +
                            keyBytes.length + " bytes. Generate one with, e.g., " +
                            "`openssl rand -base64 32` and set it via an environment " +
                            "variable / secrets manager - never commit it to source control.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private Key getSigningKey() {
        return signingKey;
    }

    public String generateToken(String email, Long userId, String role) {
        return generateToken(email, userId, role, expirationMs);
    }

    public String generateToken(String email, Long userId, String role, boolean rememberMe) {
        long ttl = rememberMe ? rememberMeExpirationMs : expirationMs;
        return generateToken(email, userId, role, ttl);
    }

    private String generateToken(String email, Long userId, String role, long ttlMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(email)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String extractRole(String token) {
        return (String) Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role");
    }

    // Needed so the filter can resolve "who is calling" server-side from the
    // signed token, instead of trusting a client-supplied header.
    public Long extractUserId(String token) {
        Object claim = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("userId");
        return claim == null ? null : Long.valueOf(claim.toString());
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
