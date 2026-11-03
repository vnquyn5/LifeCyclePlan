package com.lifestyleplanner.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtSecurityConfiguration {
    @Bean
    JwtKeyPair jwtKeyPair(JwtProperties properties) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            String privatePem = Files.readString(Path.of(properties.privateKeyPath()), StandardCharsets.US_ASCII);
            String publicPem = Files.readString(Path.of(properties.publicKeyPath()), StandardCharsets.US_ASCII);
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(privatePem, "PRIVATE KEY")));
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(decodePem(publicPem, "PUBLIC KEY")));
            if (!(privateKey instanceof RSAPrivateKey rsaPrivate) || !(publicKey instanceof RSAPublicKey rsaPublic)
                    || rsaPrivate.getModulus().bitLength() < 2048 || rsaPublic.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT keys must be RSA keys of at least 2048 bits");
            }
            byte[] challenge = "lifecycleplan-key-pair-check".getBytes(StandardCharsets.US_ASCII);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update(challenge);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(challenge);
            if (!verifier.verify(signature)) throw new IllegalStateException("JWT public/private key pair does not match");
            String kid = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
            return new JwtKeyPair(rsaPrivate, rsaPublic, kid);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load configured JWT signing keys", exception);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyPair keys) {
        RSAKey jwk = new RSAKey.Builder(keys.publicKey()).privateKey(keys.privateKey()).keyID(keys.keyId()).build();
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyPair keys, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static byte[] decodePem(String pem, String label) {
        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";
        if (!pem.contains(begin) || !pem.contains(end)) throw new IllegalArgumentException("Unexpected PEM format");
        return Base64.getMimeDecoder().decode(pem.replace(begin, "").replace(end, ""));
    }

    record JwtKeyPair(RSAPrivateKey privateKey, RSAPublicKey publicKey, String keyId) { }
}
