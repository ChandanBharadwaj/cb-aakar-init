package studio.aakar.api.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Queue filter for staff: any of {@code statuses} (all when empty) and a free-text {@code query} matched as an
 * order-number prefix ({@code AK-0001}, {@code 000012}) or as digits of the customer's phone.
 */
public record OrderSearch(Set<OrderStatus> statuses, String query) {

    public static final OrderSearch ALL = new OrderSearch(Set.of(), null);

    public OrderSearch {
        statuses = statuses == null || statuses.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(statuses));
        query = query == null || query.isBlank() ? null : query.trim();
    }
}
