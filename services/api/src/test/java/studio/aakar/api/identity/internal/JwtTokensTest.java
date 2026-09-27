package studio.aakar.api.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The customer token parser refuses staff tokens even though both are signed with the same key. */
class JwtTokensTest {

    static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs256-0123456789";
    // The parser checks expiry against the real clock, so tokens are minted "now" (a fixed instant expired
    // ten minutes after it was written and turned this suite red for everyone).
    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JwtTokens tokens = new JwtTokens(SECRET);

    @Test
    void customerTokensCarryTheCustomerTypeAndParse() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String jwt = tokens.issue(userId, sessionId, NOW, NOW.plusSeconds(600));

        assertThat(Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).build()
                .parseSignedClaims(jwt).getPayload().get(JwtTokens.TYP_CLAIM)).isEqualTo(JwtTokens.TYP_CUSTOMER);
        assertThat(tokens.parse(jwt)).hasValueSatisfying(p -> {
            assertThat(p.userId()).isEqualTo(userId);
            assertThat(p.sessionId()).isEqualTo(sessionId);
        });
    }

    @Test
    void staffTokensAreNeverCustomerSessions() {
        String staff = Jwts.builder()
                .id(UUID.randomUUID().toString()) // even with a jti that could name a session
                .subject(UUID.randomUUID().toString())
                .claim(JwtTokens.TYP_CLAIM, "staff")
                .claim("email", "studio@aakar.local")
                .claim("role", "owner")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(tokens.parse(staff)).isEmpty();
    }

    @Test
    void tokensWithoutATypeStillParseForCompatibility() {
        UUID userId = UUID.randomUUID();
        String legacy = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .expiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(tokens.parse(legacy)).map(JwtTokens.Parsed::userId).contains(userId);
    }
}
