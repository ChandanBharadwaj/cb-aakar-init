package studio.aakar.api.order.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import studio.aakar.api.order.CheckoutRequest;
import studio.aakar.api.order.CheckoutResult;
import studio.aakar.api.order.OrderDto;
import studio.aakar.api.order.OrderEventDto;
import studio.aakar.api.order.OrderSummaryDto;
import studio.aakar.api.order.Orders;
import studio.aakar.api.payment.PaymentDto;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.Identity;
import studio.aakar.api.shared.SseHub;

@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearer")
class OrderController {

    private final Orders orders;
    private final SseHub<OrderEventDto> stream;

    OrderController(Orders orders, SseHub<OrderEventDto> stream) {
        this.orders = orders;
        this.stream = stream;
    }

    @PostMapping("/checkout")
    @Tag(name = "orders")
    @Operation(summary = "Turn the cart into an order awaiting payment",
            description = "Snapshots items, prices and address; creates a payment with the configured gateway (mock by default) and "
                    + "returns where to pay. 409 `cart_empty` / `not_printable`, 422 `not_serviceable`. The cart empties when payment succeeds.")
    ResponseEntity<CheckoutResult> checkout(@Valid @RequestBody CheckoutRequest request, Identity identity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.checkout(identity.requireUser(), request));
    }

    @GetMapping("/orders")
    @Tag(name = "orders")
    @Operation(summary = "The signed-in customer's orders, newest first")
    List<OrderSummaryDto> list(Identity identity) {
        return orders.list(identity.requireUser());
    }

    @GetMapping("/orders/{orderId}")
    @Tag(name = "orders")
    @Operation(summary = "An order with items, address, payment, shipment and events", description = "404 when it is not the customer's.")
    OrderDto order(@PathVariable UUID orderId, Identity identity) {
        return own(orderId, identity);
    }

    @GetMapping(path = "/orders/{orderId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Tag(name = "orders")
    @Operation(summary = "Server-Sent Events of order stage changes",
            description = "`event: stage`, `id` = sequence, `data` = OrderEvent. Replays persisted events after `Last-Event-ID`, then streams "
                    + "live ones; `: keep-alive` every 15 s; closes after `delivered` or `cancelled`.")
    SseEmitter events(@PathVariable UUID orderId, @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            Identity identity) {
        OrderDto order = own(orderId, identity);
        int lastSeen = parseSequence(lastEventId);
        SseHub<OrderEventDto>.Subscription subscription = stream.subscribe(orderId, lastSeen);
        subscription.replayThenGoLive(orders.events(orderId, lastSeen));
        if (order.status().terminal()) {
            subscription.close();
        }
        return subscription.emitter();
    }

    @PostMapping("/orders/{orderId}/payments")
    @Tag(name = "payments")
    @Operation(summary = "Start a new payment attempt for an order still awaiting payment",
            description = "After a failed or abandoned payment. 409 `order_not_payable` unless the order is `pending_payment`.")
    ResponseEntity<PaymentDto> retryPayment(@PathVariable UUID orderId, Identity identity) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.retryPayment(identity.requireUser(), orderId));
    }

    private OrderDto own(UUID orderId, Identity identity) {
        return orders.find(identity.requireUser(), orderId).orElseThrow(() -> ApiProblemException.notFound("Order", orderId));
    }

    private static int parseSequence(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(lastEventId.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
