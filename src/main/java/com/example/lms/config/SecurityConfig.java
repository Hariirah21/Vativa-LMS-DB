package com.example.lms.config;

import com.example.lms.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${app.security.bcrypt-strength:12}")
    private int bcryptStrength;

    // FIXED: was @Value("${app.cors.allowed-origins}") with no default,
    // which is a REQUIRED placeholder - Spring refuses to start the whole
    // context if it's not set anywhere in your config (this is exactly
    // what crashed securityConfig bean creation). A localhost + prod-frontend
    // default is safe to ship (it's not a secret, unlike app.jwt.secret which
    // deliberately has no default) - override it via
    // app.cors.allowed-origins in application.yml/properties or the
    // APP_CORS_ALLOWED_ORIGINS env var (comma-separated) for staging/prod.
    @Value("${app.cors.allowed-origins:http://localhost:4200,https://vativa-lms.netlify.app}")
    private List<String> allowedOrigins;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Required by SignUpService / LoginService (constructor-injected) to
    // hash and verify passwords with BCrypt.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // Explicit header allow-list instead of "*", since allowCredentials(true)
        // + a wildcard header list is an easy-to-miss loose combination.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            RateLimitingFilter rateLimitingFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentTypeOptions -> {})
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, HttpStatus.UNAUTHORIZED, "Authentication is required."))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this action."))
                )
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // Rate limiting runs first so an abusive client is rejected
                // before it ever reaches JWT parsing or the DB.
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                // Wires JwtAuthenticationFilter into the chain - without this,
                // JwtUtil is never invoked on incoming requests and every
                // protected endpoint 401s even with a valid token.
                .addFilterAfter(jwtAuthenticationFilter, RateLimitingFilter.class);
        return http.build();
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response,
                                 HttpStatus status,
                                 String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(message)));
    }
}