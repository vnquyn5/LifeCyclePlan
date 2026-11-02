package com.lifestyleplanner.auth.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.auth-rate-limit")
public record AuthRateLimitProperties(
        int maxTrackedBuckets,
        int loginMaxRequests,
        Duration loginWindow,
        int registerMaxRequests,
        Duration registerWindow) {

    public AuthRateLimitProperties {
        if (maxTrackedBuckets < 1) {
            throw new IllegalArgumentException("maxTrackedBuckets must be positive");
        }
        validatePolicy(loginMaxRequests, loginWindow, "login");
        validatePolicy(registerMaxRequests, registerWindow, "register");
    }

    public Policy policyFor(Endpoint endpoint) {
        return switch (endpoint) {
            case LOGIN -> new Policy(loginMaxRequests, loginWindow);
            case REGISTER -> new Policy(registerMaxRequests, registerWindow);
        };
    }

    private static void validatePolicy(int maxRequests, Duration window, String name) {
        if (maxRequests < 1 || window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException(name + " rate-limit policy must be positive");
        }
    }

    public enum Endpoint {
        LOGIN,
        REGISTER
    }

    public record Policy(int maxRequests, Duration window) {
    }
}
