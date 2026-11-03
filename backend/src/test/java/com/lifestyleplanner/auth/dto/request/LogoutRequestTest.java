package com.lifestyleplanner.auth.dto.request;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

class LogoutRequestTest {

    @Test
    void requiresNonBlankRefreshToken() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new LogoutRequest(" ")).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("refreshToken")));
            assertTrue(validator.validate(new LogoutRequest("synthetic-refresh-token")).isEmpty());
        }
    }
}
