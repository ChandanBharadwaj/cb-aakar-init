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
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import studio.aakar.api.order.OrderEventDto;
import studio.aakar.api.order.OrderStage;
import studio.aakar.api.order.OrderStatus;

/** Append-only stage log per order; {@code sequence} is unique per order and is the SSE event id. */
@Entity
@Table(name = "order_events")
class OrderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private int sequence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStage stage;
    @Column(nullable = false)
    private String message;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detail;
    @Column(nullable = false)
    private Instant at;

    protected OrderEventEntity() {
    }

    OrderEventEntity(UUID orderId, int sequence, OrderStatus status, String message, Map<String, Object> detail, Instant at) {
        this.orderId = orderId;
        this.sequence = sequence;
        this.status = status;
        this.stage = status.stage();
        this.message = message;
        this.detail = detail == null || detail.isEmpty() ? null : detail;
        this.at = at;
    }

    OrderEventDto toDto() {
        return new OrderEventDto(sequence, status, stage, message, detail, at);
    }
}
