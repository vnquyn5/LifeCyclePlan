package com.lifestyleplanner.auth.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthRateLimitWebMvcConfiguration implements WebMvcConfigurer {
    private final AuthRateLimitInterceptor authRateLimitInterceptor;

    public AuthRateLimitWebMvcConfiguration(AuthRateLimitInterceptor authRateLimitInterceptor) {
        this.authRateLimitInterceptor = authRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authRateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/login", "/api/v1/auth/register");
    }
}
