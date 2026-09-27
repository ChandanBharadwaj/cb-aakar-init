package studio.aakar.api.admin.internal;

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
 * Staff access tokens: HS256 with the same key as customer tokens but {@code typ: staff}, {@code sub} = staff
 * account id, {@code email} and {@code role} claims, 12 h expiry. Parsing requires {@code typ: staff}, so a
 * customer token (typed {@code customer}, with a {@code jti}) never authenticates staff — and the customer parser
 * refuses anything typed {@code staff}.
 */
final class StaffTokens {

    static final String TYP_CLAIM = "typ";
    static final String TYP_STAFF = "staff";
    static final String EMAIL_CLAIM = "email";
    static final String ROLE_CLAIM = "role";

    private final SecretKey key;

    StaffTokens(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    String issue(StaffPrincipal staff, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .subject(staff.id().toString())
                .claim(TYP_CLAIM, TYP_STAFF)
                .claim(EMAIL_CLAIM, staff.email())
                .claim(ROLE_CLAIM, staff.role().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /** Empty for anything that is not a valid, unexpired staff token signed with our key. */
    Optional<Parsed> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!TYP_STAFF.equals(claims.get(TYP_CLAIM, String.class)) || claims.getSubject() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            return Optional.of(new Parsed(UUID.fromString(claims.getSubject()), claims.get(EMAIL_CLAIM, String.class),
                    claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    record Parsed(UUID staffId, String email, Instant expiresAt) {
    }
}
