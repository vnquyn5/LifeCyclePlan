package com.lifestyleplanner.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwsHeader;

class JwtSecurityConfigurationTest {
    private static final String ISSUER = "lifecycleplan-backend";
    private static final UUID USER_ID = UUID.fromString("54955743-d68d-4fc0-8f12-41f2cf9e79a2");

    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        JwtSecurityConfiguration configuration = new JwtSecurityConfiguration();
        JwtSecurityConfiguration.JwtKeyPair keys = new JwtSecurityConfiguration.JwtKeyPair(
                (java.security.interfaces.RSAPrivateKey) pair.getPrivate(),
                (java.security.interfaces.RSAPublicKey) pair.getPublic(), "test-kid");
        JwtProperties properties = new JwtProperties(ISSUER, Duration.ofMinutes(15), Duration.ofDays(7),
                "test-private.pem", "test-public.pem");
        encoder = configuration.jwtEncoder(keys);
        decoder = configuration.jwtDecoder(keys, properties);
    }

    @Test
    void accessJwtUsesRs256IssuerSubjectAndFifteenMinuteLifetimeWithoutPiiClaims() {
        Instant now = Instant.now();
        var jwt = decode(ISSUER, now, now.plus(Duration.ofMinutes(15)));

        assertEquals("RS256", jwt.getHeaders().get("alg"));
        assertEquals(ISSUER, jwt.getClaimAsString("iss"));
        assertEquals(USER_ID.toString(), jwt.getSubject());
        assertEquals(900, Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
        assertFalse(jwt.getClaims().containsKey("email"));
        assertFalse(jwt.getClaims().containsKey("passwordHash"));
        assertFalse(jwt.getClaims().containsKey("refreshToken"));
    }

    @Test
    void decoderRejectsWrongIssuerExpiredAndPayloadTampering() {
        Instant now = Instant.now();
        String wrongIssuer = issue("other-issuer", now, now.plusSeconds(900));
        String expired = issue(ISSUER, now.minusSeconds(1200), now.minusSeconds(300));
        String valid = issue(ISSUER, now, now.plusSeconds(900));
        String[] segments = valid.split("\\.");
        String tampered = segments[0] + "." + segments[1].substring(0, segments[1].length() - 2) + "AA." + segments[2];

        assertThrows(JwtException.class, () -> decoder.decode(wrongIssuer));
        assertThrows(JwtException.class, () -> decoder.decode(expired));
        assertThrows(JwtException.class, () -> decoder.decode(tampered));
        assertThrows(JwtException.class, () -> decoder.decode("eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0."));
    }

    private org.springframework.security.oauth2.jwt.Jwt decode(String issuer, Instant issuedAt, Instant expiresAt) {
        return decoder.decode(issue(issuer, issuedAt, expiresAt));
    }

    private String issue(String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(USER_ID.toString())
                .issuedAt(issuedAt).expiresAt(expiresAt).id(UUID.randomUUID().toString()).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
