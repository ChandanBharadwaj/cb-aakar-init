package studio.aakar.api.order;

import java.util.Map;

/** Dashboard counts; every status is a key (0 when no order is in it). Money is integer paise. */
public record OrderStats(Map<OrderStatus, Long> byStatus, long placedToday, long revenueTodayPaise, long revenueMonthPaise) {
}
