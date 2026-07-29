package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.LoginDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.UserRepository;
import com.example.lms.service.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginDto.LoginResponse>> login(@Valid @RequestBody LoginDto.LoginRequest request) {
        LoginDto.LoginResponse response = loginService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful.", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<LoginDto.LoginResponse>> me(Authentication authentication) {
        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException("User not found.", HttpStatus.NOT_FOUND));

        LoginDto.LoginResponse response = LoginDto.LoginResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
        return ResponseEntity.ok(ApiResponse.success("Profile loaded.", response));
    }
}
