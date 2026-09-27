package studio.aakar.api.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import studio.aakar.api.shared.Identity;

/** JWT issue → parse → revoke, with the session table behind it. */
class SessionServiceTest {

    static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs256-0123456789";
    static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    private final SessionRepository repository = mock(SessionRepository.class);
    private final Map<UUID, SessionEntity> saved = new HashMap<>();
    private final IdentityProperties properties = new IdentityProperties(SECRET, Duration.ofDays(7),
            new IdentityProperties.Otp("mock", true, Duration.ofMinutes(5), 5, Duration.ofMinutes(15), 5));
    private SessionService sessions;

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> {
            SessionEntity s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
            saved.put(s.id(), s);
            return s;
        });
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get(inv.<UUID>getArgument(0))));
        sessions = new SessionService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenAuthenticatesAsTheUser() {
        UUID userId = UUID.randomUUID();
        SessionService.IssuedToken token = sessions.issue(userId);

        assertThat(token.expiresInS()).isEqualTo(Duration.ofDays(7).toSeconds());
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(token.accessToken().split("\\.")).hasSize(3);

        Optional<IdentityAuthentication> auth = sessions.authenticate(token.accessToken());
        assertThat(auth).isPresent();
        assertThat(auth.get().identity()).isEqualTo(Identity.user(userId));
        assertThat(auth.get().sessionId()).isEqualTo(token.sessionId());
        assertThat(auth.get().isAuthenticated()).isTrue();
        assertThat(auth.get().getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void tokenClaimsCarryUserAndSession() {
        UUID userId = UUID.randomUUID();
        SessionService.IssuedToken token = sessions.issue(userId);

        JwtTokens.Parsed parsed = new JwtTokens(SECRET).parse(token.accessToken()).orElseThrow();
        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.sessionId()).isEqualTo(token.sessionId());
        assertThat(parsed.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void revokedTokenNoLongerAuthenticates() {
        SessionService.IssuedToken token = sessions.issue(UUID.randomUUID());
        assertThat(sessions.authenticate(token.accessToken())).isPresent();

        sessions.revoke(token.sessionId());

        assertThat(sessions.authenticate(token.accessToken())).isEmpty();
        assertThat(saved.get(token.sessionId()).revoked()).isTrue();
    }

    @Test
    void tamperedForeignAndExpiredTokensAreRejected() {
        UUID userId = UUID.randomUUID();
        SessionService.IssuedToken token = sessions.issue(userId);
        String jwt = token.accessToken();

        assertThat(sessions.authenticate(jwt.substring(0, jwt.length() - 2) + "xx")).as("tampered signature").isEmpty();
        assertThat(sessions.authenticate("not-a-jwt")).isEmpty();
        assertThat(sessions.authenticate("")).isEmpty();
        assertThat(sessions.authenticate(null)).isEmpty();

        String foreign = new JwtTokens("another-secret-that-is-also-long-enough-0123456789").issue(userId, token.sessionId(), NOW, NOW.plusSeconds(600));
        assertThat(sessions.authenticate(foreign)).as("signed with another key").isEmpty();

        String expired = new JwtTokens(SECRET).issue(userId, token.sessionId(), NOW.minusSeconds(7200), NOW.minusSeconds(3600));
        assertThat(sessions.authenticate(expired)).as("expired").isEmpty();

        String unknownSession = new JwtTokens(SECRET).issue(userId, UUID.randomUUID(), NOW, NOW.plusSeconds(600));
        assertThat(sessions.authenticate(unknownSession)).as("no session row").isEmpty();

        String otherUser = new JwtTokens(SECRET).issue(UUID.randomUUID(), token.sessionId(), NOW, NOW.plusSeconds(600));
        assertThat(sessions.authenticate(otherUser)).as("session belongs to another user").isEmpty();
    }
}
