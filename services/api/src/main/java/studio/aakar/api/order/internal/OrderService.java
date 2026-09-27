package studio.aakar.api.order.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.cart.CartDto;
import studio.aakar.api.cart.CartItemDto;
import studio.aakar.api.cart.Carts;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.Designs;
import studio.aakar.api.identity.AddressDto;
import studio.aakar.api.identity.Addresses;
import studio.aakar.api.order.CheckoutRequest;
import studio.aakar.api.order.CheckoutResult;
import studio.aakar.api.order.OrderDto;
import studio.aakar.api.order.OrderEventDto;
import studio.aakar.api.order.OrderNumbers;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.order.OrderSummaryDto;
import studio.aakar.api.order.Orders;
import studio.aakar.api.payment.PaymentDto;
import studio.aakar.api.payment.Payments;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ClockConfig;
import studio.aakar.api.shared.Identity;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.shipping.Serviceability;
import studio.aakar.api.shipping.Shipments;
import studio.aakar.api.shipping.ShippingCarrier;

@Service
class OrderService implements Orders {

    /** Studio time from confirmation to a packed parcel, on top of the carrier's transit days. */
    static final int PRODUCTION_DAYS = 5;
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderLifecycle lifecycle;
    private final OrderMapper mapper;
    private final Carts carts;
    private final Addresses addresses;
    private final Designs designs;
    private final ShippingCarrier carrier;
    private final Shipments shipments;
    private final Payments payments;
    private final ObjectMapper json;
    private final Clock clock;

    OrderService(OrderRepository orders, OrderItemRepository items, OrderLifecycle lifecycle, OrderMapper mapper, Carts carts,
            Addresses addresses, Designs designs, ShippingCarrier carrier, Shipments shipments, Payments payments, ObjectMapper json, Clock clock) {
        this.orders = orders;
        this.items = items;
        this.lifecycle = lifecycle;
        this.mapper = mapper;
        this.carts = carts;
        this.addresses = addresses;
        this.designs = designs;
        this.carrier = carrier;
        this.shipments = shipments;
        this.payments = payments;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CheckoutResult checkout(UUID userId, CheckoutRequest request) {
        CartDto cart = carts.cart(Identity.user(userId));
        if (cart.isEmpty()) {
            throw ApiProblemException.conflict(ProblemCodes.CART_EMPTY, "Cart is empty", "Add a design to the cart before checking out");
        }
        List<CartItemDto> blocked = cart.items().stream().filter(i -> !i.purchasable()).toList();
        if (!blocked.isEmpty()) {
            throw ApiProblemException.conflict(ProblemCodes.NOT_PRINTABLE, "Item not purchasable",
                    "These items can no longer be printed as they are; remove or fix them: "
                            + blocked.stream().map(CartItemDto::title).collect(Collectors.joining(", ")));
        }
        AddressDto address = addresses.find(userId, request.addressId())
                .orElseThrow(() -> ApiProblemException.notFound("Address", request.addressId()));
        Serviceability serviceability = carrier.serviceability(address.pincode());
        if (!serviceability.serviceable()) {
            throw ApiProblemException.unprocessable(ProblemCodes.NOT_SERVICEABLE, "Not serviceable",
                    "We cannot deliver to pincode " + address.pincode() + " yet", Map.of("pincode", address.pincode()));
        }

        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.STUDIO_ZONE));
        LocalDate eta = today.plusDays(PRODUCTION_DAYS + (serviceability.etaDays() == null ? 0 : serviceability.etaDays()));
        String number = OrderNumbers.format(orders.nextOrderSequence());
        OrderEntity order = orders.save(new OrderEntity(number, userId, json.convertValue(address, MAP), cart.subtotalPaise(),
                cart.shippingPaise(), cart.shippingLabel(), cart.totalPaise(), cart.policyVersion(), request.notifyWhatsappOrDefault(),
                blankToNull(request.note()), eta, now));
        int position = 0;
        for (CartItemDto line : cart.items()) {
            Map<String, Object> assets = designs.findVersion(line.versionId()).map(DesignVersionResponse::assets).orElse(null);
            items.save(new OrderItemEntity(order.id(), line.designId(), line.versionId(), line.versionNo(), line.title(), line.specsLine(),
                    line.materialId(), line.materialName(), line.qty(), json.convertValue(line.unitPrice(), MAP), line.lineTotalPaise(), assets,
                    position++));
        }
        lifecycle.placed(order, now);
        PaymentDto payment = payments.create(order.id(), number, userId, order.totalPaise());
        log.info("Order {} placed by user {}: {} line(s), {} paise, eta {}", number, userId, position, order.totalPaise(), eta);
        return new CheckoutResult(order.id(), number, payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryDto> list(UUID userId) {
        return orders.findByUserIdOrderByPlacedAtDesc(userId).stream()
                .map(order -> mapper.summary(order, items.findByOrderIdOrderBySortOrderAsc(order.id())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDto> find(UUID userId, UUID orderId) {
        return orders.findByIdAndUserId(orderId, userId).map(order -> mapper.full(order, items.findByOrderIdOrderBySortOrderAsc(order.id()),
                payments.latestForOrder(order.id()).orElse(null), shipments.forOrder(order.id()).orElse(null), lifecycle.events(order.id(), 0)));
    }

    @Override
    public List<OrderEventDto> events(UUID orderId, int afterSequence) {
        return lifecycle.events(orderId, afterSequence);
    }

    @Override
    @Transactional
    public PaymentDto retryPayment(UUID userId, UUID orderId) {
        OrderEntity order = orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiProblemException.notFound("Order", orderId));
        if (order.status() != OrderStatus.pending_payment) {
            throw ApiProblemException.conflict(ProblemCodes.ORDER_NOT_PAYABLE, "Order not awaiting payment",
                    "Order " + order.number() + " is " + order.status() + "; only an order awaiting payment can start a new payment");
        }
        return payments.create(order.id(), order.number(), userId, order.totalPaise());
    }

    @Override
    public OrderEventDto advance(UUID orderId, OrderStatus status, String message, Map<String, Object> detail) {
        return lifecycle.advance(orderId, status, message, detail);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
