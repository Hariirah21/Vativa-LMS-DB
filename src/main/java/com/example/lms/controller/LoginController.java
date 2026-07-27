package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.LoginDto;
import com.example.lms.service.LoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/login")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    @PostMapping
    public ResponseEntity<ApiResponse<LoginDto.LoginResponse>> login(@Valid @RequestBody LoginDto.LoginRequest request) {
        LoginDto.LoginResponse response = loginService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful.", response));
    }
}