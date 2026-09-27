package studio.aakar.api.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.shared.Identity;

/** Issues, authenticates and revokes access tokens; every token is backed by a {@code sessions} row. */
@Service
class SessionService {

    private final SessionRepository sessions;
    private final JwtTokens tokens;
    private final IdentityProperties properties;
    private final Clock clock;

    SessionService(SessionRepository sessions, IdentityProperties properties, Clock clock) {
        this(sessions, new JwtTokens(properties.jwtSecret()), properties, clock);
    }

    SessionService(SessionRepository sessions, JwtTokens tokens, IdentityProperties properties, Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public IssuedToken issue(UUID userId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.tokenTtl());
        SessionEntity session = sessions.save(new SessionEntity(userId, now, expiresAt));
        String token = tokens.issue(userId, session.id(), now, expiresAt);
        return new IssuedToken(token, session.id(), expiresAt, properties.tokenTtl().toSeconds());
    }

    /** The authentication for a bearer token, or empty when it is malformed, expired, unknown or revoked. */
    @Transactional(readOnly = true)
    public Optional<IdentityAuthentication> authenticate(String token) {
        Instant now = clock.instant();
        return tokens.parse(token)
                .filter(parsed -> parsed.expiresAt().isAfter(now))
                .flatMap(parsed -> sessions.findById(parsed.sessionId())
                        .filter(session -> session.userId().equals(parsed.userId()) && session.usable(now))
                        .map(session -> new IdentityAuthentication(Identity.user(session.userId()), session.id())));
    }

    @Transactional
    public void revoke(UUID sessionId) {
        sessions.findById(sessionId).ifPresent(session -> session.revoke(clock.instant()));
    }

    record IssuedToken(String accessToken, UUID sessionId, Instant expiresAt, long expiresInS) {
    }
}
