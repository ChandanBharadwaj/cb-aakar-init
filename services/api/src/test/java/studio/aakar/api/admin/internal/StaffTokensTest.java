package studio.aakar.api.admin.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import studio.aakar.api.admin.StaffRole;

/** Staff tokens are typed {@code staff}; a customer token (typed {@code customer}, same key) never passes. */
class StaffTokensTest {

    static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs256-0123456789";
    // The parser checks expiry against the real clock, so tokens are minted "now" (a fixed 27 Sep instant expired
    // with its 12 h token and turned this test red, as JwtTokensTest's did before).
    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final StaffTokens tokens = new StaffTokens(SECRET);
    private final StaffPrincipal owner = new StaffPrincipal(UUID.randomUUID(), "studio@aakar.local", "Aakar Studio", StaffRole.owner);

    @Test
    void issuedTokenParsesBackToTheStaffMember() {
        String jwt = tokens.issue(owner, NOW, NOW.plus(Duration.ofHours(12)));
        assertThat(jwt.split("\\.")).hasSize(3);

        StaffTokens.Parsed parsed = tokens.parse(jwt).orElseThrow();
        assertThat(parsed.staffId()).isEqualTo(owner.id());
        assertThat(parsed.email()).isEqualTo("studio@aakar.local");
        assertThat(parsed.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void customerTokensSignedWithTheSameKeyAreRejected() {
        String customer = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(UUID.randomUUID().toString())
                .claim("typ", "customer")
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(tokens.parse(customer)).as("typ=customer").isEmpty();

        String untyped = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(UUID.randomUUID().toString())
                .expiration(Date.from(NOW.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(tokens.parse(untyped)).as("no typ claim").isEmpty();
    }

    @Test
    void tamperedForeignAndMalformedTokensAreRejected() {
        String jwt = tokens.issue(owner, NOW, NOW.plus(Duration.ofHours(12)));
        assertThat(tokens.parse(jwt.substring(0, jwt.length() - 2) + "xx")).isEmpty();
        assertThat(tokens.parse(new StaffTokens("another-secret-that-is-also-long-enough-0123456789").issue(owner, NOW, NOW.plusSeconds(60)))).isEmpty();
        assertThat(tokens.parse("not-a-jwt")).isEmpty();
        assertThat(tokens.parse("")).isEmpty();
        assertThat(tokens.parse(null)).isEmpty();
    }
}
