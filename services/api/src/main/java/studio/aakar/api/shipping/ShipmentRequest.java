package studio.aakar.api.shipping;

import java.util.UUID;

/** What the carrier needs to book a shipment: the order and the delivery address. */
public record ShipmentRequest(
        UUID orderId,
        String orderNumber,
        String name,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String pincode) {
}
