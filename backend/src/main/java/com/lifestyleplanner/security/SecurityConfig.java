package com.lifestyleplanner.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import com.lifestyleplanner.user.domain.entity.User;
import com.lifestyleplanner.user.repository.UserRepository;
import java.util.UUID;


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
            HttpSecurity http,
            UserRepository users
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
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/refresh"
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

        Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter = jwt -> {
            try {
                UUID userId = UUID.fromString(jwt.getSubject());
                User user = users.findById(userId).filter(User::canLogin)
                        .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error("invalid_token")));
                return new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(user.getId(), user.getEmail()), null,
                        AuthorityUtils.createAuthorityList("ROLE_USER"));
            } catch (IllegalArgumentException exception) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
            }
        };
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(authenticationEntryPoint));

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
