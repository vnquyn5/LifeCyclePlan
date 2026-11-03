package com.lifestyleplanner.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl,
        String privateKeyPath, String publicKeyPath) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank() || accessTokenTtl == null || accessTokenTtl.isNegative()
                || accessTokenTtl.isZero() || refreshTokenTtl == null || refreshTokenTtl.isNegative()
                || refreshTokenTtl.isZero() || privateKeyPath == null || privateKeyPath.isBlank()
                || publicKeyPath == null || publicKeyPath.isBlank()) {
            throw new IllegalArgumentException("JWT configuration is incomplete");
        }
    }
}
