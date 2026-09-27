package studio.aakar.api.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import studio.aakar.api.order.OrderEventDto;
import studio.aakar.api.order.OrderStage;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.payment.PaymentFailed;
import studio.aakar.api.payment.PaymentSucceeded;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.SseHub;
import studio.aakar.api.shipping.ShipmentDto;
import studio.aakar.api.shipping.ShipmentRequest;
import studio.aakar.api.shipping.Shipments;

/** {@code OrderService.advance} and the payment hand-offs, with the repositories and shipping mocked. */
class OrderLifecycleTest {

    static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderEventRepository events = mock(OrderEventRepository.class);
    private final Shipments shipments = mock(Shipments.class);
    private final List<OrderEventEntity> appended = new ArrayList<>();
    private OrderLifecycle lifecycle;
    private OrderEntity order;

    @BeforeEach
    void setUp() {
        order = new OrderEntity("AK-000007", UUID.randomUUID(), Map.of("name", "Asha Rao", "phone", "+919876543210", "line1", "12 MG Road",
                "city", "Bengaluru", "state", "Karnataka", "pincode", "560001"), 114_900, 0, "Shipping · Delhivery, 4 days · Free", 114_900,
                "2026-09-phase0", true, null, LocalDate.of(2026, 10, 6), NOW);
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        when(orders.lockById(order.id())).thenReturn(Optional.of(order));
        when(events.maxSequence(order.id())).thenAnswer(inv -> appended.size());
        when(events.saveAndFlush(any())).thenAnswer(inv -> {
            OrderEventEntity e = inv.getArgument(0);
            appended.add(e);
            return e;
        });
        lifecycle = new OrderLifecycle(orders, events, shipments, new SseHub<>("stage", OrderEventDto::sequence, e -> e.status().terminal()),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void advanceMovesTheOrderAndAppendsTheNextEvent() {
        order.moveTo(OrderStatus.queued, NOW);
        appended.add(new OrderEventEntity(order.id(), 1, OrderStatus.pending_payment, "Awaiting payment", null, NOW));

        OrderEventDto event = lifecycle.advance(order.id(), OrderStatus.slicing, null, Map.of("printer_bay", "B2", "layer_height_mm", 0.2));

        assertThat(order.status()).isEqualTo(OrderStatus.slicing);
        assertThat(event.sequence()).isEqualTo(2);
        assertThat(event.status()).isEqualTo(OrderStatus.slicing);
        assertThat(event.stage()).isEqualTo(OrderStage.slicing);
        assertThat(event.message()).isEqualTo("Slicing your design");
        assertThat(event.detail()).containsEntry("printer_bay", "B2");
        assertThat(event.at()).isEqualTo(NOW);
        verifyNoInteractions(shipments);
    }

    @Test
    void customMessageWins() {
        order.moveTo(OrderStatus.slicing, NOW);
        OrderEventDto event = lifecycle.advance(order.id(), OrderStatus.printing, "Layer 1 of 480 · bay B2", null);
        assertThat(event.message()).isEqualTo("Layer 1 of 480 · bay B2");
        assertThat(event.detail()).isNull();
    }

    @Test
    void illegalTransitionIs409AndChangesNothing() {
        order.moveTo(OrderStatus.queued, NOW);

        assertThatThrownBy(() -> lifecycle.advance(order.id(), OrderStatus.printing, null, null))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> {
                    assertThat(e.status().value()).isEqualTo(409);
                    assertThat(e.code()).isEqualTo("invalid_transition");
                    assertThat(e.getMessage()).contains("AK-000007").contains("queued").contains("printing");
                });
        assertThat(order.status()).isEqualTo(OrderStatus.queued);
        assertThat(appended).isEmpty();
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void unknownOrderIs404() {
        UUID missing = UUID.randomUUID();
        when(orders.lockById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> lifecycle.advance(missing, OrderStatus.queued, null, null))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.code()).isEqualTo("not_found"));
    }

    @Test
    void packedBooksTheShipmentFromTheAddressSnapshot() {
        order.moveTo(OrderStatus.qc, NOW);
        when(shipments.create(any())).thenReturn(new ShipmentDto(UUID.randomUUID(), "mock-delhivery", "MOCK0000000001", "created", null, null, List.of()));

        lifecycle.advance(order.id(), OrderStatus.packed, null, null);

        ArgumentCaptor<ShipmentRequest> request = ArgumentCaptor.forClass(ShipmentRequest.class);
        verify(shipments).create(request.capture());
        assertThat(request.getValue().orderId()).isEqualTo(order.id());
        assertThat(request.getValue().orderNumber()).isEqualTo("AK-000007");
        assertThat(request.getValue().name()).isEqualTo("Asha Rao");
        assertThat(request.getValue().pincode()).isEqualTo("560001");
        assertThat(request.getValue().line2()).isNull();
        assertThat(order.status()).isEqualTo(OrderStatus.packed);
    }

    @Test
    void shippedAndDeliveredAddTrackingEvents() {
        ShipmentDto shipment = new ShipmentDto(UUID.randomUUID(), "mock-delhivery", "MOCK0000000001", "created", null, null, List.of());
        when(shipments.forOrder(order.id())).thenReturn(Optional.of(shipment));
        order.moveTo(OrderStatus.packed, NOW);

        lifecycle.advance(order.id(), OrderStatus.shipped, null, null);
        verify(shipments).addEvent(eq(order.id()), eq("in_transit"), eq("Handed to mock-delhivery · MOCK0000000001"));

        lifecycle.advance(order.id(), OrderStatus.delivered, null, null);
        verify(shipments).addEvent(eq(order.id()), eq("delivered"), eq("Delivered"));
        assertThat(order.status()).isEqualTo(OrderStatus.delivered);
        assertThat(appended).extracting(e -> e.toDto().status()).containsExactly(OrderStatus.shipped, OrderStatus.delivered);

        assertThatThrownBy(() -> lifecycle.advance(order.id(), OrderStatus.cancelled, null, null))
                .isInstanceOfSatisfying(ApiProblemException.class, e -> assertThat(e.code()).isEqualTo("invalid_transition"));
    }

    @Test
    void shippedWithoutAShipmentRowStillAdvances() {
        when(shipments.forOrder(order.id())).thenReturn(Optional.empty());
        order.moveTo(OrderStatus.packed, NOW);
        lifecycle.advance(order.id(), OrderStatus.shipped, null, null);
        verify(shipments, never()).addEvent(any(), any(), any());
        assertThat(order.status()).isEqualTo(OrderStatus.shipped);
    }

    @Test
    void paymentSucceededConfirmsAndQueuesInOneGo() {
        appended.add(new OrderEventEntity(order.id(), 1, OrderStatus.pending_payment, "Awaiting payment", null, NOW));
        PaymentSucceeded payment = new PaymentSucceeded(UUID.randomUUID(), order.id(), order.userId(), 114_900, "INV-2026-000001", "upi");

        Optional<OrderEntity> confirmed = lifecycle.paymentSucceeded(payment);

        assertThat(confirmed).contains(order);
        assertThat(order.status()).isEqualTo(OrderStatus.queued);
        List<OrderEventDto> dtos = appended.stream().map(OrderEventEntity::toDto).toList();
        assertThat(dtos).extracting(OrderEventDto::sequence).containsExactly(1, 2, 3);
        assertThat(dtos).extracting(OrderEventDto::status).containsExactly(OrderStatus.pending_payment, OrderStatus.confirmed, OrderStatus.queued);
        assertThat(dtos).extracting(OrderEventDto::stage).containsExactly(OrderStage.payment, OrderStage.queued, OrderStage.queued);
        assertThat(dtos.get(1).message()).isEqualTo("Order confirmed · payment received");
        assertThat(dtos.get(1).detail()).containsEntry("invoice_number", "INV-2026-000001").containsEntry("method", "upi");
        assertThat(dtos.get(2).message()).isEqualTo("Queued at studio");
        assertThat(dtos.get(2).detail()).containsEntry("studio", "Bengaluru");
    }

    @Test
    void paymentSucceededForANonPendingOrderIsIgnored() {
        order.moveTo(OrderStatus.queued, NOW);
        Optional<OrderEntity> result = lifecycle.paymentSucceeded(new PaymentSucceeded(UUID.randomUUID(), order.id(), order.userId(), 1, "INV", "upi"));
        assertThat(result).isEmpty();
        assertThat(appended).isEmpty();
        assertThat(order.status()).isEqualTo(OrderStatus.queued);

        UUID unknown = UUID.randomUUID();
        when(orders.lockById(unknown)).thenReturn(Optional.empty());
        assertThat(lifecycle.paymentSucceeded(new PaymentSucceeded(UUID.randomUUID(), unknown, order.userId(), 1, "INV", "upi"))).isEmpty();
    }

    @Test
    void paymentFailedKeepsTheOrderAwaitingPaymentAndSaysSo() {
        appended.add(new OrderEventEntity(order.id(), 1, OrderStatus.pending_payment, "Awaiting payment", null, NOW));
        UUID paymentId = UUID.randomUUID();

        lifecycle.paymentFailed(new PaymentFailed(paymentId, order.id(), order.userId()));

        assertThat(order.status()).isEqualTo(OrderStatus.pending_payment);
        OrderEventDto failed = appended.get(1).toDto();
        assertThat(failed.sequence()).isEqualTo(2);
        assertThat(failed.status()).isEqualTo(OrderStatus.pending_payment);
        assertThat(failed.stage()).isEqualTo(OrderStage.payment);
        assertThat(failed.message()).isEqualTo("Payment failed");
        assertThat(failed.detail()).containsEntry("payment_id", paymentId.toString());

        // once confirmed, a late failure of an old attempt is ignored
        order.moveTo(OrderStatus.queued, NOW);
        lifecycle.paymentFailed(new PaymentFailed(UUID.randomUUID(), order.id(), order.userId()));
        assertThat(appended).hasSize(2);
    }
}
