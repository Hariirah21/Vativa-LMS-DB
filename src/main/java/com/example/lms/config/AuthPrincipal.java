package com.example.lms.config;

import lombok.AllArgsConstructor;
import lombok.Getter;


@Getter
@AllArgsConstructor
public class AuthPrincipal {
    private final Long id;
    private final String email;
    private final String role;
}
