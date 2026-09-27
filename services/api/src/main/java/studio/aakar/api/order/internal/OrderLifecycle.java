package studio.aakar.api.order.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import studio.aakar.api.order.OrderEventDto;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.payment.PaymentFailed;
import studio.aakar.api.payment.PaymentSucceeded;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.shared.SseHub;
import studio.aakar.api.shipping.ShipmentRequest;
import studio.aakar.api.shipping.Shipments;

/**
 * The order state machine. Every change locks the order row, checks {@link OrderTransitions}, appends an
 * {@code order_events} row with the next sequence and pushes it to SSE subscribers after commit. Side
 * effects: the shipment is booked at {@code packed}; tracking events are added at {@code shipped} and
 * {@code delivered}.
 */
@Service
class OrderLifecycle {

    static final String STUDIO = "Bengaluru";
    static final String MESSAGE_AWAITING_PAYMENT = "Awaiting payment";
    static final String MESSAGE_CONFIRMED = "Order confirmed · payment received";
    static final String MESSAGE_QUEUED = "Queued at studio";
    static final String MESSAGE_PAYMENT_FAILED = "Payment failed";
    private static final Logger log = LoggerFactory.getLogger(OrderLifecycle.class);

    private final OrderRepository orders;
    private final OrderEventRepository events;
    private final Shipments shipments;
    private final SseHub<OrderEventDto> stream;
    private final Clock clock;

    OrderLifecycle(OrderRepository orders, OrderEventRepository events, Shipments shipments, SseHub<OrderEventDto> stream, Clock clock) {
        this.orders = orders;
        this.events = events;
        this.shipments = shipments;
        this.stream = stream;
        this.clock = clock;
    }

    @Transactional
    public OrderEventDto advance(UUID orderId, OrderStatus to, String message, Map<String, Object> detail) {
        OrderEntity order = lock(orderId);
        OrderEventDto event = transition(order, to, message, detail, clock.instant());
        publishAfterCommit(orderId, List.of(event));
        return event;
    }

    /** First event of a new order; called inside the checkout transaction. */
    OrderEventDto placed(OrderEntity order, Instant now) {
        OrderEventDto event = append(order, OrderStatus.pending_payment, MESSAGE_AWAITING_PAYMENT, null, now);
        publishAfterCommit(order.id(), List.of(event));
        return event;
    }

    /**
     * Payment succeeded: {@code pending_payment → confirmed → queued} in one go. Returns the order when it
     * was confirmed by this call; empty when the order is unknown or was not awaiting payment (logged, ignored).
     */
    @Transactional
    public Optional<OrderEntity> paymentSucceeded(PaymentSucceeded payment) {
        Optional<OrderEntity> found = orders.lockById(payment.orderId());
        if (found.isEmpty()) {
            log.warn("Payment {} succeeded for unknown order {}", payment.paymentId(), payment.orderId());
            return Optional.empty();
        }
        OrderEntity order = found.get();
        if (order.status() != OrderStatus.pending_payment) {
            log.warn("Payment {} succeeded but order {} is {}; ignoring (needs manual reconciliation)", payment.paymentId(), order.number(),
                    order.status());
            return Optional.empty();
        }
        Instant now = clock.instant();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("invoice_number", payment.invoiceNumber());
        if (payment.method() != null) {
            detail.put("method", payment.method());
        }
        List<OrderEventDto> emitted = new ArrayList<>();
        emitted.add(transition(order, OrderStatus.confirmed, MESSAGE_CONFIRMED, detail, now));
        emitted.add(transition(order, OrderStatus.queued, MESSAGE_QUEUED, Map.of("studio", STUDIO), now));
        publishAfterCommit(order.id(), emitted);
        log.info("Order {} confirmed by payment {} and queued", order.number(), payment.paymentId());
        return Optional.of(order);
    }

    /** Payment failed: the order stays awaiting payment; the board shows why. */
    @Transactional
    public void paymentFailed(PaymentFailed payment) {
        orders.lockById(payment.orderId()).filter(o -> o.status() == OrderStatus.pending_payment).ifPresent(order -> {
            OrderEventDto event = append(order, OrderStatus.pending_payment, MESSAGE_PAYMENT_FAILED,
                    Map.of("payment_id", payment.paymentId().toString()), clock.instant());
            publishAfterCommit(order.id(), List.of(event));
            log.info("Order {}: payment {} failed", order.number(), payment.paymentId());
        });
    }

    @Transactional(readOnly = true)
    public List<OrderEventDto> events(UUID orderId, int afterSequence) {
        return events.findByOrderIdAndSequenceGreaterThanOrderBySequenceAsc(orderId, afterSequence).stream()
                .map(OrderEventEntity::toDto)
                .toList();
    }

    private OrderEventDto transition(OrderEntity order, OrderStatus to, String message, Map<String, Object> detail, Instant now) {
        OrderStatus from = order.status();
        if (!OrderTransitions.allowed(from, to)) {
            throw ApiProblemException.conflict(ProblemCodes.INVALID_TRANSITION, "Invalid order transition",
                    "Order " + order.number() + " cannot go from " + from + " to " + to + "; allowed next: " + OrderTransitions.next(from));
        }
        order.moveTo(to, now);
        OrderEventDto event = append(order, to, message == null || message.isBlank() ? OrderTransitions.defaultMessage(to) : message, detail, now);
        switch (to) {
            case packed -> shipments.create(shipmentRequest(order));
            case shipped -> shipments.forOrder(order.id())
                    .ifPresent(s -> shipments.addEvent(order.id(), "in_transit", "Handed to " + s.carrier() + (s.awb() == null ? "" : " · " + s.awb())));
            case delivered -> shipments.forOrder(order.id()).ifPresent(s -> shipments.addEvent(order.id(), "delivered", "Delivered"));
            default -> { }
        }
        log.info("Order {}: {} → {} ({})", order.number(), from, to, event.message());
        return event;
    }

    private OrderEventDto append(OrderEntity order, OrderStatus status, String message, Map<String, Object> detail, Instant at) {
        int sequence = events.maxSequence(order.id()) + 1;
        return events.saveAndFlush(new OrderEventEntity(order.id(), sequence, status, message, detail, at)).toDto();
    }

    private OrderEntity lock(UUID orderId) {
        return orders.lockById(orderId).orElseThrow(() -> ApiProblemException.notFound("Order", orderId));
    }

    private static ShipmentRequest shipmentRequest(OrderEntity order) {
        return new ShipmentRequest(order.id(), order.number(), order.addressField("name"), order.addressField("phone"),
                order.addressField("line1"), order.addressField("line2"), order.addressField("city"), order.addressField("state"),
                order.addressField("pincode"));
    }

    private void publishAfterCommit(UUID orderId, List<OrderEventDto> emitted) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emitted.forEach(e -> stream.publish(orderId, e));
                }
            });
        } else {
            emitted.forEach(e -> stream.publish(orderId, e));
        }
    }
}
