package com.cathy.frauddetection.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    // 56 bytes: jjwt selects the HMAC algorithm by key length, matching production.
    private static final String SECRET =
            "test-only-secret-key-with-at-least-fifty-six-bytes-abcdefg";
    private static final String OTHER_SECRET =
            "different-secret-key-with-at-least-fifty-six-bytes-abcdef";

    private final JwtService service = new JwtService(SECRET, Duration.ofMinutes(30));

    @Test
    void parsesBackTheClaimsItIssued() {
        String token = service.issue("analyst", Role.ANALYST);

        Optional<Claims> claims = service.parse(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("analyst");
        assertThat(claims.get().get(JwtService.ROLE_CLAIM)).isEqualTo("ANALYST");
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        JwtService attacker = new JwtService(OTHER_SECRET, Duration.ofMinutes(30));
        String forged = attacker.issue("admin", Role.ADMIN);

        assertThat(service.parse(forged)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        // Negative duration puts expiry before issuedAt, so the token is born expired.
        JwtService expiring = new JwtService(SECRET, Duration.ofSeconds(-1));
        String stale = expiring.issue("analyst", Role.ANALYST);

        assertThat(service.parse(stale)).isEmpty();
    }

    @Test
    void rejectsMalformedToken() {
        assertThat(service.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesToStartWithAKeyShorterThan256Bits() {
        assertThatThrownBy(() -> new JwtService("too-short", Duration.ofMinutes(30)))
                .isInstanceOf(WeakKeyException.class);
    }
}
