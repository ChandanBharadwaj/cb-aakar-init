package studio.aakar.api.pricing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One published pricing policy version; {@code policy} is the {@code PricingPolicy} as snake_case JSON. */
@Entity
@Table(name = "pricing_policies")
class PricingPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String version;
    @Column(nullable = false)
    private boolean active;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> policy;
    private String note;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    protected PricingPolicyEntity() {
    }

    PricingPolicyEntity(String version, boolean active, Map<String, Object> policy, Instant createdAt, String createdBy) {
        this(version, active, policy, null, createdAt, createdBy);
    }

    PricingPolicyEntity(String version, boolean active, Map<String, Object> policy, String note, Instant createdAt, String createdBy) {
        this.version = version;
        this.active = active;
        this.policy = policy;
        this.note = note;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    String version() {
        return version;
    }

    boolean active() {
        return active;
    }

    Map<String, Object> policy() {
        return policy;
    }

    String note() {
        return note;
    }

    Instant createdAt() {
        return createdAt;
    }

    String createdBy() {
        return createdBy;
    }

    void activate() {
        this.active = true;
    }

    void deactivate() {
        this.active = false;
    }
}
