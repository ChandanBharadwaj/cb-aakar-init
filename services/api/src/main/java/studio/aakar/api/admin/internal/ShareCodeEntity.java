package studio.aakar.api.admin.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The code behind the packaging card's {@code /k/{code}} link: one per order, pointing at the piece printed. */
@Entity
@Table(name = "share_codes")
class ShareCodeEntity {

    @Id
    private String code;
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;
    @Column(name = "design_id", nullable = false)
    private UUID designId;
    @Column(name = "version_id", nullable = false)
    private UUID versionId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ShareCodeEntity() {
    }

    ShareCodeEntity(String code, UUID orderId, UUID designId, UUID versionId, Instant now) {
        this.code = code;
        this.orderId = orderId;
        this.designId = designId;
        this.versionId = versionId;
        this.createdAt = now;
    }

    String code() {
        return code;
    }

    UUID orderId() {
        return orderId;
    }

    UUID designId() {
        return designId;
    }

    UUID versionId() {
        return versionId;
    }
}
