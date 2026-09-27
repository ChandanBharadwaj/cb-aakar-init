package studio.aakar.api.admin.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import studio.aakar.api.order.OrderStats;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.order.Orders;
import studio.aakar.api.shared.ClockConfig;

/**
 * Counts by status, orders and paid revenue for today and this month (studio days, Asia/Kolkata), and how many
 * orders wait for a staff step ({@code queued}, {@code finishing}, {@code qc}).
 */
@Service
class DashboardService {

    private final Orders orders;
    private final Clock clock;

    DashboardService(Orders orders, Clock clock) {
        this.orders = orders;
        this.clock = clock;
    }

    DashboardDto dashboard() {
        ZonedDateTime now = clock.instant().atZone(ClockConfig.STUDIO_ZONE);
        LocalDate today = now.toLocalDate();
        Instant todayStart = today.atStartOfDay(ClockConfig.STUDIO_ZONE).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ClockConfig.STUDIO_ZONE).toInstant();
        return from(orders.stats(todayStart, monthStart));
    }

    static DashboardDto from(OrderStats stats) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            byStatus.put(status.name(), stats.byStatus().getOrDefault(status, 0L));
        }
        return new DashboardDto(byStatus, stats.placedToday(), stats.revenueTodayPaise(), stats.revenueMonthPaise(), awaitingAction(stats.byStatus()));
    }

    /** Orders that need a staff step: queued (pick up), finishing (sanding done?) and qc (pass or reprint). */
    static long awaitingAction(Map<OrderStatus, Long> byStatus) {
        return byStatus.getOrDefault(OrderStatus.queued, 0L) + byStatus.getOrDefault(OrderStatus.finishing, 0L)
                + byStatus.getOrDefault(OrderStatus.qc, 0L);
    }
}
