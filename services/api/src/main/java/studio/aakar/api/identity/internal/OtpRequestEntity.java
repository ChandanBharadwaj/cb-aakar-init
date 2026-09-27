package studio.aakar.api.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One issued sign-in code; only its SHA-256 is stored. */
@Entity
@Table(name = "otp_requests")
class OtpRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String phone;
    @Column(name = "code_hash", nullable = false)
    private String codeHash;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected OtpRequestEntity() {
    }

    OtpRequestEntity(String phone, String codeHash, Instant createdAt, Instant expiresAt) {
        this.phone = phone;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    UUID id() {
        return id;
    }

    String phone() {
        return phone;
    }

    String codeHash() {
        return codeHash;
    }

    int attempts() {
        return attempts;
    }

    Instant expiresAt() {
        return expiresAt;
    }

    boolean verified() {
        return verifiedAt != null;
    }

    void recordFailedAttempt() {
        attempts++;
    }

    void markVerified(Instant now) {
        this.verifiedAt = now;
    }
}
