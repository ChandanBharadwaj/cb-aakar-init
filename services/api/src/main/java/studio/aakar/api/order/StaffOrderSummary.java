package studio.aakar.api.order;

import java.util.List;
import java.util.UUID;

/**
 * A queue row for staff: the customer-facing summary plus who ordered, which materials the lines use and the
 * statuses the order may move to next (the transition table).
 */
public record StaffOrderSummary(OrderSummaryDto summary, UUID userId, List<String> materials, List<OrderStatus> nextActions) {
}
