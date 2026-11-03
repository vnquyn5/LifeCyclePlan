package com.lifestyleplanner.auth.session.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lifestyleplanner.auth.dto.response.TokenResponse;
import com.lifestyleplanner.auth.session.domain.entity.AuthRefreshSession;
import com.lifestyleplanner.auth.session.domain.entity.AuthRefreshToken;
import com.lifestyleplanner.auth.session.domain.entity.SessionRevocationReason;
import com.lifestyleplanner.auth.session.domain.valueobject.TokenHash;
import com.lifestyleplanner.auth.session.repository.AuthRefreshSessionRepository;
import com.lifestyleplanner.auth.session.repository.AuthRefreshTokenRepository;
import com.lifestyleplanner.security.JwtProperties;
import com.lifestyleplanner.user.domain.entity.User;

@Service
public class AuthSessionService {
    private final AuthRefreshSessionRepository sessions;
    private final AuthRefreshTokenRepository tokens;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthSessionService(AuthRefreshSessionRepository sessions, AuthRefreshTokenRepository tokens,
            JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse issue(User user) {
        Instant now = clock.instant();
        UUID sessionId = UUID.randomUUID();
        sessions.save(new AuthRefreshSession(sessionId, user, now));
        return issueTokenPair(user, sessionId, now);
    }

    @Transactional
    public Optional<TokenResponse> refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return Optional.empty();
        TokenHash hash = hash(rawRefreshToken);
        Optional<UUID> candidate = tokens.findSessionIdByTokenHash(hash);
        if (candidate.isEmpty()) return Optional.empty();

        Optional<AuthRefreshSession> lockedSession = sessions.findByIdForUpdate(candidate.get());
        if (lockedSession.isEmpty()) return Optional.empty();
        AuthRefreshSession session = lockedSession.get();
        AuthRefreshToken token = tokens.findByTokenHashForUpdate(hash).orElse(null);
        if (token == null) return Optional.empty();

        Instant now = clock.instant();
        if (token.getReplacedByTokenId() != null) {
            revokeFamily(session, now, SessionRevocationReason.REFRESH_REUSE);
            return Optional.empty();
        }
        if (session.isRevoked() || !token.isUsableAt(now)) return Optional.empty();
        User user = session.getUser();
        if (!user.canLogin()) {
            revokeFamily(session, now, SessionRevocationReason.USER_UNAVAILABLE);
            return Optional.empty();
        }

        TokenResponse response = issueTokenPair(user, session.getId(), now);
        AuthRefreshToken replacement = tokens.findByTokenHash(hash(response.refreshToken())).orElseThrow();
        token.markRotated(now, replacement.getId());
        return Optional.of(response);
    }

    private TokenResponse issueTokenPair(User user, UUID sessionId, Instant now) {
        String rawRefreshToken = newRefreshSecret();
        AuthRefreshToken token = new AuthRefreshToken(user, sessionId, hash(rawRefreshToken), now,
                now.plus(properties.refreshTokenTtl()));
        tokens.saveAndFlush(token);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(accessToken, rawRefreshToken, "Bearer", properties.accessTokenTtl().toSeconds());
    }

    private void revokeFamily(AuthRefreshSession session, Instant now, SessionRevocationReason reason) {
        session.revoke(now, reason);
        List<AuthRefreshToken> family = tokens.findAllBySessionId(session.getId());
        family.forEach(token -> token.revoke(now));
    }

    private String newRefreshSecret() {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private static TokenHash hash(String rawToken) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return new TokenHash(java.util.HexFormat.of().formatHex(digest));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
