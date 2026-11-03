package com.lifestyleplanner.auth.dto.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lifestyleplanner.auth.exception.AuthErrorCode;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RefreshTokenRequestTest {
    @Test
    void refreshTokenIsRequired() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<RefreshTokenRequest>> violations =
                    validator.validate(new RefreshTokenRequest("  "));

            assertEquals(1, violations.size());
            assertEquals("refreshToken", violations.iterator().next().getPropertyPath().toString());
        }
    }

    @Test
    void validRefreshRequestPassesValidationAndInvalidTokensMapToGenericUnauthorized() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(new RefreshTokenRequest("synthetic-refresh-token")).isEmpty());
        }

        assertEquals("AUTH_INVALID_REFRESH_TOKEN", AuthErrorCode.INVALID_REFRESH_TOKEN.getCode());
        assertEquals(401, AuthErrorCode.INVALID_REFRESH_TOKEN.getHttpStatus().value());
    }
}
