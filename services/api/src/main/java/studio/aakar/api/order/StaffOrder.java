package studio.aakar.api.order;

import java.util.List;
import java.util.UUID;

/** A full order for staff: the customer-facing order plus who ordered and the allowed next statuses. */
public record StaffOrder(OrderDto order, UUID userId, List<OrderStatus> nextActions) {
}
