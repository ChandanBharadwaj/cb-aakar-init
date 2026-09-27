package studio.aakar.api.shipping;

import java.util.Optional;
import java.util.UUID;

/** Public API of the shipping module for the order module. */
public interface Shipments {

    /** Books with the carrier and records the shipment ({@code created}); one per order. */
    ShipmentDto create(ShipmentRequest request);

    Optional<ShipmentDto> forOrder(UUID orderId);

    /** Appends a tracking event and moves the shipment to {@code status}. */
    ShipmentDto addEvent(UUID orderId, String status, String message);
}
