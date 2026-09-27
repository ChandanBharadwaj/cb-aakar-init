package studio.aakar.api.order;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import studio.aakar.api.payment.PaymentDto;
import studio.aakar.api.shared.PageDto;

/** Public API of the order module. */
public interface Orders {

    /**
     * Turns the user's cart into an order awaiting payment and starts a payment with the configured gateway.
     * 409 {@code cart_empty} / {@code not_printable}, 404 for a foreign address, 422 {@code not_serviceable}.
     */
    CheckoutResult checkout(UUID userId, CheckoutRequest request);

    /** The user's orders, newest first. */
    List<OrderSummaryDto> list(UUID userId);

    /** The order only when it belongs to {@code userId}. */
    Optional<OrderDto> find(UUID userId, UUID orderId);

    /** Persisted events with sequence greater than {@code afterSequence}, oldest first. */
    List<OrderEventDto> events(UUID orderId, int afterSequence);

    /** A fresh payment attempt while the order is {@code pending_payment} (409 {@code order_not_payable} otherwise). */
    PaymentDto retryPayment(UUID userId, UUID orderId);

    /**
     * Moves an order to {@code status} (studio operations, driven by the management API).
     * Checks the transition table (409 {@code invalid_transition}), appends the event, books the shipment at
     * {@code packed} and adds a tracking event at {@code shipped} and {@code delivered}.
     *
     * @param message customer-facing line for the tracking board; a default is used when blank
     * @param detail extra data for the board (studio, printer bay, layer height)
     */
    OrderEventDto advance(UUID orderId, OrderStatus status, String message, Map<String, Object> detail);

    // ---- staff (management API, ADR-0012) ------------------------------------------------------------------------

    /** The studio queue across every customer, newest first, filtered by status and a number/phone query. */
    PageDto<StaffOrderSummary> search(OrderSearch search, int page, int size);

    /** Any order by id with the moves it may make next, regardless of owner. */
    Optional<StaffOrder> findForStaff(UUID orderId);

    /**
     * Dashboard counts: orders per status, orders placed since {@code todayStart}, and revenue (sum of
     * {@code total_paise} of non-cancelled orders with a succeeded payment) placed since {@code todayStart}
     * and since {@code monthStart}.
     */
    OrderStats stats(Instant todayStart, Instant monthStart);
}
