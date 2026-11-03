package com.lifestyleplanner.auth.session.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lifestyleplanner.auth.session.domain.entity.AuthRefreshSession;

public interface AuthRefreshSessionRepository extends JpaRepository<AuthRefreshSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthRefreshSession session where session.id = :id")
    Optional<AuthRefreshSession> findByIdForUpdate(@Param("id") UUID id);
}
