package com.nexuspro.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // Disable CSRF — using stateless JWT + SameSite cookies
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Stateless — no sessions
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/api/v1/auth/register",
                                 "/api/v1/auth/login",
                                 "/api/v1/auth/refresh",
                                 "/api/v1/auth/verify-email",
                                 "/api/v1/auth/forgot-password",
                                 "/api/v1/auth/reset-password").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Everything else needs authentication (verified by gateway's X-User-Id header)
                .anyRequest().authenticated()
            )
            // Security headers
            .headers(headers -> headers
                .frameOptions(fo -> fo.deny())
                .contentTypeOptions(ct -> {})
                .xssProtection(xss -> {})
                .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(pp -> pp.policy("camera=(), microphone=(), geolocation=()"))
            )
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with cost factor 12 — strong against brute force
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Lock down to specific origins — no wildcard in production
        config.setAllowedOrigins(List.of(
            "https://nexuspro.co.uk",
            "https://www.nexuspro.co.uk",
            "https://app.nexuspro.co.uk",
            "http://localhost:3000",    // Dev only
            "http://localhost:5173"     // Vite dev only
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        config.setAllowCredentials(true);  // Required for cookies
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
