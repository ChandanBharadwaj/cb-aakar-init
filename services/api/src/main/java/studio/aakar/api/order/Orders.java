package studio.aakar.api.order;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import studio.aakar.api.payment.PaymentDto;

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
     * Moves an order to {@code status} (studio operations; staff endpoints arrive with the admin module).
     * Checks the transition table (409 {@code invalid_transition}), appends the event, books the shipment at
     * {@code packed} and adds a tracking event at {@code shipped} and {@code delivered}.
     *
     * @param message customer-facing line for the tracking board; a default is used when blank
     * @param detail extra data for the board (studio, printer bay, layer height)
     */
    OrderEventDto advance(UUID orderId, OrderStatus status, String message, Map<String, Object> detail);
}
