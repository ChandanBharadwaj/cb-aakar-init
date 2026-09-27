package studio.aakar.api.cart.internal;

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

/** A version in a finish, with the price snapshot ({@code price-breakdown.v1.json}) it was last priced at. */
@Entity
@Table(name = "cart_items")
class CartItemEntity implements CartMerge.Line {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "cart_id", nullable = false)
    private UUID cartId;
    @Column(name = "version_id", nullable = false)
    private UUID versionId;
    @Column(name = "material_id", nullable = false)
    private String materialId;
    @Column(nullable = false)
    private int qty;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unit_price", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> unitPrice;
    @Column(name = "policy_version", nullable = false)
    private String policyVersion;
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected CartItemEntity() {
    }

    CartItemEntity(UUID cartId, UUID versionId, String materialId, int qty, Map<String, Object> unitPrice, String policyVersion, Instant now) {
        this.cartId = cartId;
        this.versionId = versionId;
        this.materialId = materialId;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.policyVersion = policyVersion;
        this.addedAt = now;
    }

    UUID id() {
        return id;
    }

    UUID cartId() {
        return cartId;
    }

    @Override
    public UUID versionId() {
        return versionId;
    }

    @Override
    public String materialId() {
        return materialId;
    }

    @Override
    public int qty() {
        return qty;
    }

    Map<String, Object> unitPrice() {
        return unitPrice;
    }

    String policyVersion() {
        return policyVersion;
    }

    Instant addedAt() {
        return addedAt;
    }

    void setQty(int qty) {
        this.qty = qty;
    }

    void reprice(String materialId, Map<String, Object> unitPrice, String policyVersion) {
        this.materialId = materialId;
        this.unitPrice = unitPrice;
        this.policyVersion = policyVersion;
    }

    void moveTo(UUID cartId) {
        this.cartId = cartId;
    }
}
