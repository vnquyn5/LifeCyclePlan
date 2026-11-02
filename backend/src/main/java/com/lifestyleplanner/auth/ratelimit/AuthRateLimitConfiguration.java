package com.lifestyleplanner.auth.ratelimit;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthRateLimitProperties.class)
public class AuthRateLimitConfiguration {

    @Bean
    public Clock authRateLimitClock() {
        return Clock.systemUTC();
    }

    @Bean
    public AuthRateLimiter authRateLimiter(AuthRateLimitProperties properties, Clock authRateLimitClock) {
        return new AuthRateLimiter(properties, authRateLimitClock);
    }
}
