package studio.aakar.api.shipping;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** {@code Shipment} in the OpenAPI document. */
public record ShipmentDto(UUID id, String carrier, String awb, String status, LocalDate eta, String trackingUrl, List<Event> events) {

    public record Event(String status, String message, Instant at) {
    }
}
