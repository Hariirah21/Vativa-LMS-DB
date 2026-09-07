package com.example.lms.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Required by SignUpService / LoginService (constructor-injected) to
    // hash and verify passwords with BCrypt.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses/*/enrollments",
                                "/api/courses/*/enrollments/**")
                                .hasAnyRole("ADMIN", "SUPER_ADMIN", "INSTRUCTOR")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/courses",
                                "/api/courses/**")
                                .hasAnyRole("ADMIN", "SUPER_ADMIN", "INSTRUCTOR")
                        .requestMatchers(HttpMethod.PUT, "/api/courses/**")
                                .hasAnyRole("ADMIN", "SUPER_ADMIN", "INSTRUCTOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/courses/**")
                                .hasAnyRole("ADMIN", "SUPER_ADMIN", "INSTRUCTOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/courses/**")
                                .hasAnyRole("ADMIN", "SUPER_ADMIN", "INSTRUCTOR")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/courses",
                                "/api/courses/**")
                                .authenticated()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"Authentication is required.\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"You are not authorized to perform this action.\"}");
                        }))
                // Wires JwtAuthenticationFilter into the chain - without this,
                // JwtUtil is never invoked on incoming requests and every
                // protected endpoint 401s even with a valid token.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
