package studio.aakar.api.shipping.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import studio.aakar.api.shipping.ShipmentDto;

@Entity
@Table(name = "shipments")
class ShipmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;
    @Column(nullable = false)
    private String carrier;
    private String awb;
    @Column(nullable = false)
    private String status;
    private LocalDate eta;
    @Column(name = "tracking_url")
    private String trackingUrl;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> events;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ShipmentEntity() {
    }

    ShipmentEntity(UUID orderId, String carrier, String awb, LocalDate eta, String trackingUrl, Instant now) {
        this.orderId = orderId;
        this.carrier = carrier;
        this.awb = awb;
        this.status = "created";
        this.eta = eta;
        this.trackingUrl = trackingUrl;
        this.events = new ArrayList<>();
        this.createdAt = now;
        this.updatedAt = now;
        addEvent("created", "Shipment booked with " + carrier + (awb == null ? "" : " · AWB " + awb), now);
    }

    void addEvent(String status, String message, Instant now) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("status", status);
        event.put("message", message);
        event.put("at", now.toString());
        List<Map<String, Object>> updated = new ArrayList<>(events == null ? List.of() : events);
        updated.add(event);
        this.events = updated;
        this.status = status;
        this.updatedAt = now;
    }

    ShipmentDto toDto() {
        List<ShipmentDto.Event> list = events == null ? List.of() : events.stream()
                .map(e -> new ShipmentDto.Event(String.valueOf(e.get("status")), String.valueOf(e.get("message")),
                        e.get("at") == null ? null : Instant.parse(String.valueOf(e.get("at")))))
                .toList();
        return new ShipmentDto(id, carrier, awb, status, eta, trackingUrl, list);
    }
}
