package studio.aakar.api.order.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;
import studio.aakar.api.order.OrderDto;
import studio.aakar.api.order.OrderEventDto;
import studio.aakar.api.order.OrderItemDto;
import studio.aakar.api.order.OrderSummaryDto;
import studio.aakar.api.payment.PaymentDto;
import studio.aakar.api.pricing.PriceBreakdown;
import studio.aakar.api.shipping.ShipmentDto;

@Component
class OrderMapper {

    private final ObjectMapper json;

    OrderMapper(ObjectMapper json) {
        this.json = json;
    }

    OrderSummaryDto summary(OrderEntity order, List<OrderItemEntity> items) {
        return new OrderSummaryDto(order.id(), order.number(), order.status(), order.status().stage(), title(items), order.totalPaise(),
                items.stream().mapToInt(OrderItemEntity::qty).sum(), order.eta(), order.placedAt());
    }

    OrderDto full(OrderEntity order, List<OrderItemEntity> items, PaymentDto payment, ShipmentDto shipment, List<OrderEventDto> events) {
        OrderSummaryDto s = summary(order, items);
        return new OrderDto(s.id(), s.number(), s.status(), s.stage(), s.title(), s.totalPaise(), s.itemsCount(), s.eta(), s.placedAt(),
                items.stream().map(this::item).toList(), order.address(), order.subtotalPaise(), order.shippingPaise(), order.shippingLabel(),
                order.policyVersion(), payment, shipment, events, order.notifyWhatsapp(), order.note());
    }

    OrderItemDto item(OrderItemEntity i) {
        return new OrderItemDto(i.id(), i.designId(), i.versionId(), i.versionNo(), i.title(), i.specsLine(), i.materialId(), i.materialName(),
                i.qty(), json.convertValue(i.unitPrice(), PriceBreakdown.class), i.lineTotalPaise(), i.assets());
    }

    /** First item's title plus how many other lines there are: {@code Jharokha Phone Stand + 2 more}. */
    static String title(List<OrderItemEntity> items) {
        if (items.isEmpty()) {
            return "";
        }
        String first = items.get(0).title();
        int others = items.size() - 1;
        return others == 0 ? first : first + " + " + others + " more";
    }
}
