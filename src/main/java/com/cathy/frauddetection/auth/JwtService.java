package com.cathy.frauddetection.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the signed tokens that carry a caller's identity.
 *
 * <p>A JWT's header and payload are Base64-encoded, not encrypted: anyone
 * holding the token can read the claims. Only the signature is secret-backed,
 * so the token proves who the caller is but hides nothing. Never put anything
 * here the user should not see.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Claim carrying the single role; read back by the authentication filter. */
    static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") Duration expiration) {
        // Throws WeakKeyException below 256 bits, which is what HS256 requires.
        // Failing at startup beats issuing brute-forceable tokens in production.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /** Builds a signed token naming the user and their role. */
    public String issue(String username, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature and expiry, returning the claims only if both hold.
     *
     * <p>Every failure mode collapses into an empty Optional on purpose:
     * telling a caller whether a token was expired, forged or malformed hands
     * an attacker information. The distinction is logged, not returned.
     */
    public Optional<Claims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
