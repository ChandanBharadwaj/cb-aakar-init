package studio.aakar.api.admin.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import studio.aakar.api.order.OrderStats;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.order.Orders;

/** Dashboard aggregation: studio-day windows in Asia/Kolkata and the awaiting-action count. */
class DashboardServiceTest {

    private final Orders orders = mock(Orders.class);

    @Test
    void windowsStartAtMidnightInAsiaKolkata() {
        // 20:30 UTC on 27 Sep is already 02:00 IST on 28 Sep: today is the 28th, the month started on the 1st (IST).
        Clock clock = Clock.fixed(Instant.parse("2026-09-27T20:30:00Z"), ZoneOffset.UTC);
        when(orders.stats(any(), any())).thenReturn(new OrderStats(Map.of(), 0, 0, 0));

        new DashboardService(orders, clock).dashboard();

        ArgumentCaptor<Instant> today = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> month = ArgumentCaptor.forClass(Instant.class);
        verify(orders).stats(today.capture(), month.capture());
        assertThat(today.getValue()).isEqualTo(Instant.parse("2026-09-27T18:30:00Z")); // 28 Sep 00:00 IST
        assertThat(month.getValue()).isEqualTo(Instant.parse("2026-08-31T18:30:00Z")); // 1 Sep 00:00 IST
    }

    @Test
    void awaitingActionIsQueuedPlusFinishingPlusQc() {
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        byStatus.put(OrderStatus.queued, 3L);
        byStatus.put(OrderStatus.finishing, 2L);
        byStatus.put(OrderStatus.qc, 1L);
        byStatus.put(OrderStatus.printing, 7L);
        byStatus.put(OrderStatus.delivered, 40L);

        DashboardDto dashboard = DashboardService.from(new OrderStats(byStatus, 4, 229_800, 4_596_000));

        assertThat(dashboard.awaitingAction()).isEqualTo(6);
        assertThat(dashboard.ordersToday()).isEqualTo(4);
        assertThat(dashboard.revenueTodayPaise()).isEqualTo(229_800);
        assertThat(dashboard.revenueMonthPaise()).isEqualTo(4_596_000);
        assertThat(dashboard.ordersByStatus()).containsEntry("queued", 3L).containsEntry("printing", 7L).containsEntry("cancelled", 0L);
        assertThat(dashboard.ordersByStatus()).hasSize(OrderStatus.values().length); // every status is a key, zero or not
        assertThat(DashboardService.awaitingAction(Map.of())).isZero();
    }
}
