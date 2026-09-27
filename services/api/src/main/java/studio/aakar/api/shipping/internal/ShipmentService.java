package studio.aakar.api.shipping.internal;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shipping.CarrierShipment;
import studio.aakar.api.shipping.ShipmentDto;
import studio.aakar.api.shipping.ShipmentRequest;
import studio.aakar.api.shipping.Shipments;
import studio.aakar.api.shipping.ShippingCarrier;

@Service
class ShipmentService implements Shipments {

    static final Set<String> STATUSES = Set.of("created", "picked_up", "in_transit", "out_for_delivery", "delivered", "returned");

    private final ShipmentRepository shipments;
    private final ShippingCarrier carrier;
    private final Clock clock;

    ShipmentService(ShipmentRepository shipments, ShippingCarrier carrier, Clock clock) {
        this.shipments = shipments;
        this.carrier = carrier;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ShipmentDto create(ShipmentRequest request) {
        Optional<ShipmentEntity> existing = shipments.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            return existing.get().toDto();
        }
        CarrierShipment booked = carrier.createShipment(request);
        return shipments.save(new ShipmentEntity(request.orderId(), booked.carrier(), booked.awb(), booked.eta(), booked.trackingUrl(),
                clock.instant())).toDto();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShipmentDto> forOrder(UUID orderId) {
        return shipments.findByOrderId(orderId).map(ShipmentEntity::toDto);
    }

    @Override
    @Transactional
    public ShipmentDto addEvent(UUID orderId, String status, String message) {
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("Unknown shipment status " + status);
        }
        ShipmentEntity shipment = shipments.findByOrderId(orderId)
                .orElseThrow(() -> ApiProblemException.notFound("Shipment for order", orderId));
        shipment.addEvent(status, message, clock.instant());
        return shipment.toDto();
    }
}
