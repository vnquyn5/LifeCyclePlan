package com.lifestyleplanner.auth.session.domain.valueobject;

public record TokenHash(String value) {
    public TokenHash {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("token hash must be a lowercase SHA-256 hex digest");
        }
    }
}
