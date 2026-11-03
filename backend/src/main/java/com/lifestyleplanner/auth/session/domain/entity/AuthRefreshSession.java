package com.lifestyleplanner.auth.session.domain.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.lifestyleplanner.user.domain.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "auth_refresh_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_auth_refresh_sessions_id_user",
                columnNames = {"id", "user_id"}))
public class AuthRefreshSession {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoked_reason", length = 32)
    private SessionRevocationReason revokedReason;

    protected AuthRefreshSession() {
    }

    public AuthRefreshSession(UUID id, User user, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must be provided");
        this.user = Objects.requireNonNull(user, "user must be provided");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must be provided");
    }

    public void revoke(Instant revokedAt, SessionRevocationReason reason) {
        Instant revocationTime = Objects.requireNonNull(revokedAt, "revokedAt must be provided");
        SessionRevocationReason revocationReason = Objects.requireNonNull(reason, "reason must be provided");
        if (revocationTime.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt cannot be before createdAt");
        }
        if (this.revokedAt == null) {
            this.revokedAt = revocationTime;
            this.revokedReason = revocationReason;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public SessionRevocationReason getRevokedReason() {
        return revokedReason;
    }
}
