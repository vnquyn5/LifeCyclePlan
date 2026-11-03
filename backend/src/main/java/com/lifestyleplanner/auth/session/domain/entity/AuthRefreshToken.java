package com.lifestyleplanner.auth.session.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.lifestyleplanner.auth.session.domain.valueobject.TokenHash;
import com.lifestyleplanner.auth.session.infrastructure.persistence.TokenHashConverter;
import com.lifestyleplanner.user.domain.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_refresh_tokens")
public class AuthRefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Convert(converter = TokenHashConverter.class)
    @Column(name = "token_hash", nullable = false, length = 64, unique = true, updatable = false)
    private TokenHash tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    protected AuthRefreshToken() {
    }

    public AuthRefreshToken(
            User user,
            UUID sessionId,
            TokenHash tokenHash,
            Instant createdAt,
            Instant expiresAt) {
        this.user = Objects.requireNonNull(user, "user must be provided");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must be provided");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must be provided");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must be provided");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must be provided");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public boolean isUsableAt(Instant now) {
        Objects.requireNonNull(now, "now must be provided");
        return revokedAt == null && replacedByTokenId == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant revokedAt) {
        Instant revocationTime = Objects.requireNonNull(revokedAt, "revokedAt must be provided");
        if (revocationTime.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt cannot be before createdAt");
        }
        if (this.revokedAt == null) {
            this.revokedAt = revocationTime;
        }
    }

    public void markRotated(Instant rotatedAt, UUID replacementId) {
        Instant rotationTime = Objects.requireNonNull(rotatedAt, "rotatedAt must be provided");
        UUID newTokenId = Objects.requireNonNull(replacementId, "replacementId must be provided");
        if (newTokenId.equals(id)) {
            throw new IllegalArgumentException("replacement token must have a different id");
        }
        if (rotationTime.isBefore(createdAt)) {
            throw new IllegalArgumentException("rotatedAt cannot be before createdAt");
        }
        if (revokedAt != null || replacedByTokenId != null) {
            throw new IllegalStateException("refresh token has already been consumed");
        }
        this.revokedAt = rotationTime;
        this.replacedByTokenId = newTokenId;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public TokenHash getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedByTokenId() {
        return replacedByTokenId;
    }
}
