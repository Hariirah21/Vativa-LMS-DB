package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.SignUpDto;
import com.example.lms.service.SignUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth/signup")
@RequiredArgsConstructor
public class SignUpController {

    private final SignUpService signUpService;

    @PostMapping
    public ResponseEntity<ApiResponse<SignUpDto.SignUpResponse>> signUp(@Valid @RequestBody SignUpDto.SignUpRequest request) {
        SignUpDto.SignUpResponse response = signUpService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created successfully.", response));
    }
}
