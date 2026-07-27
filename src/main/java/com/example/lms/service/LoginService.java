package com.example.lms.service;

import com.example.lms.dto.LoginDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.UserRepository;
import com.example.lms.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Business logic for 02__US_Login.
 *
 * Deliberately returns the same generic "Invalid Email ID or Password"
 * error for both "email not found" and "wrong password" (matches the
 * Field List error text and avoids leaking which part was wrong).
 *
 * "Remember Me": when true, issues a longer-lived JWT (see
 * JwtUtil.generateToken(..., rememberMe)) instead of tracking a
 * separate session, since the API is stateless.
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginDto.LoginResponse login(LoginDto.LoginRequest request) {
        try {
            User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                    .orElseThrow(() -> new ApiException("Invalid Email ID or Password.", HttpStatus.UNAUTHORIZED));

            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new ApiException("Invalid Email ID or Password.", HttpStatus.UNAUTHORIZED);
            }

            if (!Boolean.TRUE.equals(user.getActive())) {
                throw new ApiException("Your account is inactive. Please contact support.", HttpStatus.FORBIDDEN);
            }

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
}