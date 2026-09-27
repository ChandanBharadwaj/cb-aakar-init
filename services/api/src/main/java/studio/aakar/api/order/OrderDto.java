package studio.aakar.api.order;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import studio.aakar.api.payment.PaymentDto;
import studio.aakar.api.shipping.ShipmentDto;

/** {@code Order} in the OpenAPI document: the summary plus items, address, totals, payment, shipment and events. */
public record OrderDto(
        UUID id,
        String number,
        OrderStatus status,
        OrderStage stage,
        String title,
        long totalPaise,
        int itemsCount,
        LocalDate eta,
        Instant placedAt,
        List<OrderItemDto> items,
        Map<String, Object> address,
        long subtotalPaise,
        long shippingPaise,
        String shippingLabel,
        String policyVersion,
        PaymentDto payment,
        ShipmentDto shipment,
        List<OrderEventDto> events,
        boolean notifyWhatsapp,
        String note) {
}
