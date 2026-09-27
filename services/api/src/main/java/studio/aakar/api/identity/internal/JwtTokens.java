package studio.aakar.api.identity.internal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * HS256 access tokens: {@code sub} = user id, {@code jti} = session id, {@code exp} = session expiry,
 * {@code typ} = {@code customer}. Parsing verifies the signature and expiry and refuses any token typed
 * otherwise — a staff token from the management API ({@code typ: staff}, same key) never authenticates a
 * customer; revocation is checked against {@code sessions} by {@link SessionService}.
 */
final class JwtTokens {

    static final String TYP_CLAIM = "typ";
    static final String TYP_CUSTOMER = "customer";

    private final SecretKey key;

    JwtTokens(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    String issue(UUID userId, UUID sessionId, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .id(sessionId.toString())
                .subject(userId.toString())
                .claim(TYP_CLAIM, TYP_CUSTOMER)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /** Empty for anything that is not a valid, unexpired token signed with our key. */
    Optional<Parsed> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (claims.getId() == null || claims.getSubject() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            Object typ = claims.get(TYP_CLAIM);
            if (typ != null && !TYP_CUSTOMER.equals(String.valueOf(typ))) {
                return Optional.empty(); // a staff token is never a customer session
            }
            return Optional.of(new Parsed(UUID.fromString(claims.getSubject()), UUID.fromString(claims.getId()),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    record Parsed(UUID userId, UUID sessionId, Instant expiresAt) {
    }
}
