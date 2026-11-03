package com.lifestyleplanner.auth.session.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.lifestyleplanner.auth.session.domain.valueobject.TokenHash;
import com.lifestyleplanner.user.domain.entity.User;

class AuthRefreshTokenTest {
    private static final Instant CREATED_AT = Instant.parse("2026-11-02T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-03T00:00:00Z");
    private static final TokenHash TOKEN_HASH = new TokenHash("a".repeat(64));

    @Test
    void newTokenIsUsableUntilExpiry() {
        AuthRefreshToken token = token();

        assertTrue(token.isUsableAt(CREATED_AT));
        assertTrue(token.isUsableAt(EXPIRES_AT.minusNanos(1)));
        assertFalse(token.isUsableAt(EXPIRES_AT));
        assertNull(token.getRevokedAt());
    }

    @Test
    void revokedTokenCannotBeUsedAndRepeatedRevocationIsIdempotent() {
        AuthRefreshToken token = token();
        Instant revokedAt = CREATED_AT.plusSeconds(60);

        token.revoke(revokedAt);
        token.revoke(revokedAt.plusSeconds(60));

        assertFalse(token.isUsableAt(revokedAt));
        assertEquals(revokedAt, token.getRevokedAt());
    }

    @Test
    void constructorRejectsInvalidHashAndExpiry() {
        User user = new User("synthetic@example.test", "synthetic-hash");

        assertThrows(IllegalArgumentException.class, () -> new TokenHash("plaintext-refresh-token"));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthRefreshToken(user, UUID.randomUUID(), TOKEN_HASH, EXPIRES_AT, CREATED_AT));
    }

    private AuthRefreshToken token() {
        return new AuthRefreshToken(
                new User("synthetic@example.test", "synthetic-hash"),
                UUID.randomUUID(),
                TOKEN_HASH,
                CREATED_AT,
                EXPIRES_AT);
    }
}
