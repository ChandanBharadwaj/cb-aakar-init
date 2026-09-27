package studio.aakar.api.order.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import studio.aakar.api.order.OrderStatus;

@Entity
@Table(name = "orders")
class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String number;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> address;
    @Column(name = "subtotal_paise", nullable = false)
    private long subtotalPaise;
    @Column(name = "shipping_paise", nullable = false)
    private long shippingPaise;
    @Column(name = "shipping_label")
    private String shippingLabel;
    @Column(name = "total_paise", nullable = false)
    private long totalPaise;
    @Column(name = "policy_version", nullable = false)
    private String policyVersion;
    @Column(name = "notify_whatsapp", nullable = false)
    private boolean notifyWhatsapp;
    private String note;
    private LocalDate eta;
    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderEntity() {
    }

    OrderEntity(String number, UUID userId, Map<String, Object> address, long subtotalPaise, long shippingPaise, String shippingLabel,
            long totalPaise, String policyVersion, boolean notifyWhatsapp, String note, LocalDate eta, Instant now) {
        this.number = number;
        this.userId = userId;
        this.status = OrderStatus.pending_payment;
        this.address = address;
        this.subtotalPaise = subtotalPaise;
        this.shippingPaise = shippingPaise;
        this.shippingLabel = shippingLabel;
        this.totalPaise = totalPaise;
        this.policyVersion = policyVersion;
        this.notifyWhatsapp = notifyWhatsapp;
        this.note = note;
        this.eta = eta;
        this.placedAt = now;
        this.updatedAt = now;
    }

    UUID id() {
        return id;
    }

    String number() {
        return number;
    }

    UUID userId() {
        return userId;
    }

    OrderStatus status() {
        return status;
    }

    Map<String, Object> address() {
        return address;
    }

    long subtotalPaise() {
        return subtotalPaise;
    }

    long shippingPaise() {
        return shippingPaise;
    }

    String shippingLabel() {
        return shippingLabel;
    }

    long totalPaise() {
        return totalPaise;
    }

    String policyVersion() {
        return policyVersion;
    }

    boolean notifyWhatsapp() {
        return notifyWhatsapp;
    }

    String note() {
        return note;
    }

    LocalDate eta() {
        return eta;
    }

    Instant placedAt() {
        return placedAt;
    }

    void moveTo(OrderStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    /** A field of the address snapshot, as text. */
    String addressField(String key) {
        Object value = address == null ? null : address.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
