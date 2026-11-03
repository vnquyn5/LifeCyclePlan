package com.lifestyleplanner.auth.dto.response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifestyleplanner.user.domain.enums.OnboardingStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loginResponseRequiresUserAndNonNullTokenPairAndSerializesContractFields() throws Exception {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            TokenResponse tokenResponse = new TokenResponse("access", "refresh", "Bearer", 900L);
            AuthUserResponse user = new AuthUserResponse(
                    UUID.randomUUID(), "synthetic@example.com", OnboardingStatus.NOT_STARTED);

            assertTrue(validator.validate(new LoginResponse(user, tokenResponse)).isEmpty());
            assertTrue(validator.validate(new AuthUserResponse(
                            UUID.randomUUID(), " ", OnboardingStatus.NOT_STARTED)).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("email")));
            Set<String> nullTokenPaths = validator.validate(new LoginResponse(user, null)).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(nullTokenPaths.contains("tokens"));
            assertTrue(validator.validate(new LoginResponse(null, tokenResponse)).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("user")));

            JsonNode json = objectMapper.valueToTree(new LoginResponse(user, tokenResponse));
            assertTrue(json.hasNonNull("user"));
            assertTrue(json.hasNonNull("tokens"));
            assertEquals("Bearer", json.at("/tokens/tokenType").asText());
            assertEquals(900, json.at("/tokens/expiresIn").asLong());
            assertTrue(json.at("/user/userId").isTextual());
        }
    }

    @Test
    void tokenResponseRejectsMissingOrInvalidFields() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertTrue(validator.validate(new TokenResponse(" ", "refresh", "Bearer", 900L)).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("accessToken")));
            assertTrue(validator.validate(new TokenResponse("access", "refresh", "Basic", 900L)).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("tokenType")));
            assertTrue(validator.validate(new TokenResponse("access", "refresh", "Bearer", 0L)).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("expiresIn")));
        }
    }
}
