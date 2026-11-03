package com.lifestyleplanner.auth.session.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lifestyleplanner.auth.session.domain.entity.AuthRefreshToken;
import com.lifestyleplanner.auth.session.domain.valueobject.TokenHash;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, UUID> {
    Optional<AuthRefreshToken> findByTokenHash(TokenHash tokenHash);

    @Query("select token.sessionId from AuthRefreshToken token where token.tokenHash = :tokenHash")
    Optional<UUID> findSessionIdByTokenHash(@Param("tokenHash") TokenHash tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AuthRefreshToken token where token.tokenHash = :tokenHash")
    Optional<AuthRefreshToken> findByTokenHashForUpdate(@Param("tokenHash") TokenHash tokenHash);

    List<AuthRefreshToken> findAllBySessionId(UUID sessionId);
}
