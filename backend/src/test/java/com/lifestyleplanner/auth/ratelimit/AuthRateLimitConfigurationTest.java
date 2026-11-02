package com.lifestyleplanner.auth.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

class AuthRateLimitConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    AuthRateLimitConfiguration.class,
                    AuthRateLimitWebMvcConfiguration.class,
                    AuthRateLimitInterceptor.class,
                    ObjectMapperTestConfiguration.class)
            .withPropertyValues(
                    "app.security.auth-rate-limit.max-tracked-buckets=10000",
                    "app.security.auth-rate-limit.login-max-requests=5",
                    "app.security.auth-rate-limit.login-window=60s",
                    "app.security.auth-rate-limit.register-max-requests=3",
                    "app.security.auth-rate-limit.register-window=10m");

    @Test
    void bindsPoliciesAndWiresTheMvcInterceptor() {
        contextRunner.run(context -> {
            assertTrue(context.isRunning());
            AuthRateLimitProperties properties = context.getBean(AuthRateLimitProperties.class);
            assertEquals(5, properties.loginMaxRequests());
            assertEquals(60, properties.loginWindow().toSeconds());
            assertEquals(3, properties.registerMaxRequests());
            assertEquals(600, properties.registerWindow().toSeconds());
            assertTrue(context.containsBean("authRateLimitInterceptor"));
            assertTrue(context.containsBean("authRateLimiter"));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ObjectMapperTestConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
