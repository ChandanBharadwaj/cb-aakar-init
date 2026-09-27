package studio.aakar.api.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One issued access token; {@code id} is the token's {@code jti}. Logout sets {@code revoked_at}. */
@Entity
@Table(name = "sessions")
class SessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected SessionEntity() {
    }

    SessionEntity(UUID userId, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    UUID id() {
        return id;
    }

    UUID userId() {
        return userId;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    boolean revoked() {
        return revokedAt != null;
    }

    boolean usable(Instant now) {
        return !revoked() && now.isBefore(expiresAt);
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
