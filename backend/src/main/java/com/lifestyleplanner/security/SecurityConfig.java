package com.lifestyleplanner.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.security.web.SecurityFilterChain;


/**
 * Cấu hình Spring Security.
 */
@Configuration
public class SecurityConfig {

    private final RestAuthenticationEntryPoint
            authenticationEntryPoint;

    private final RestAccessDeniedHandler
            accessDeniedHandler;


    public SecurityConfig(
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {

        this.authenticationEntryPoint =
                authenticationEntryPoint;

        this.accessDeniedHandler =
                accessDeniedHandler;
    }


    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http

                // REST API không dùng CSRF session form.
                .csrf(
                        csrf ->
                                csrf.disable()
                )

                // Backend stateless.
                .sessionManagement(
                        session ->
                                session
                                        .sessionCreationPolicy(
                                                SessionCreationPolicy
                                                        .STATELESS
                                        )
                )

                // Authorization rules.
                .authorizeHttpRequests(

                        auth ->
                                auth

                                        // Register/Login public.
                                        .requestMatchers(
                                                "/api/v1/auth/register",
                                                "/api/v1/auth/login"
                                        )
                                        .permitAll()

                                        // Swagger có thể public ở dev.
                                        .requestMatchers(
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**"
                                        )
                                        .permitAll()

                                        // Endpoint khác cần authentication.
                                        .anyRequest()
                                        .authenticated()
                )

                // Chuẩn hóa lỗi 401/403 từ Security Filter Chain.
                .exceptionHandling(

                        exceptions ->
                                exceptions

                                        .authenticationEntryPoint(
                                                authenticationEntryPoint
                                        )

                                        .accessDeniedHandler(
                                                accessDeniedHandler
                                        )
                );

        return http.build();
    }

    /**
     * AUTH-02 will provide token-based authentication. Until then, do not let
     * Spring Boot create and log a generated in-memory user password.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Username/password authentication is not configured");
        };
    }
}
