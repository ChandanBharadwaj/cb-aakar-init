package studio.aakar.api.admin.internal;

import java.util.Map;

/** The studio board's numbers ({@code GET /admin/api/dashboard}). Money is integer paise. */
record DashboardDto(Map<String, Long> ordersByStatus, long ordersToday, long revenueTodayPaise, long revenueMonthPaise, long awaitingAction) {
}
