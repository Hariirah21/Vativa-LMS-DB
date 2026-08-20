package com.example.lms.service;

import com.example.lms.dto.LoginDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.login.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${app.login.lockout-minutes:15}")
    private int lockoutMinutes;

    @Transactional
    public LoginDto.LoginResponse login(LoginDto.LoginRequest request) {
        try {
            User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                    .orElseThrow(() -> new ApiException("Invalid Email ID or Password.", HttpStatus.UNAUTHORIZED));

            if (user.isLocked()) {
                throw new ApiException(
                        "Too many failed login attempts. Please try again after " + lockoutMinutes + " minutes.",
                        HttpStatus.LOCKED);
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                registerFailedAttempt(user);
                throw new ApiException("Invalid Email ID or Password.", HttpStatus.UNAUTHORIZED);
            }

            if (!Boolean.TRUE.equals(user.getActive())) {
                throw new ApiException("Your account is inactive. Please contact support.", HttpStatus.FORBIDDEN);
            }

            resetFailedAttempts(user);

            boolean rememberMe = Boolean.TRUE.equals(request.getRememberMe());
            String token = jwtUtil.generateToken(user.getEmail(), user.getId(), user.getRole(), rememberMe);

            return LoginDto.LoginResponse.builder()
                    .token(token)
                    .tokenType("Bearer")
                    .userId(user.getId())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .build();

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // SRS edge case #6 - exact required wording
            throw new ApiException("Unable to log in. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() == null ? 1 : user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutMinutes));
        }
        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        if ((user.getFailedLoginAttempts() != null && user.getFailedLoginAttempts() > 0)
                || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}
