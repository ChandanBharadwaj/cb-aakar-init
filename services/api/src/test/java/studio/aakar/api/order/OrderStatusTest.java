package studio.aakar.api.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** PLAN §11.1: thirteen internal statuses collapse onto the board's eight stages. */
class OrderStatusTest {

    @Test
    void everyStatusMapsToItsCustomerStage() {
        Map<OrderStatus, OrderStage> expected = Map.ofEntries(
                Map.entry(OrderStatus.pending_payment, OrderStage.payment),
                Map.entry(OrderStatus.confirmed, OrderStage.queued),
                Map.entry(OrderStatus.queued, OrderStage.queued),
                Map.entry(OrderStatus.on_hold, OrderStage.queued),
                Map.entry(OrderStatus.slicing, OrderStage.slicing),
                Map.entry(OrderStatus.printing, OrderStage.printing),
                Map.entry(OrderStatus.reprint, OrderStage.printing),
                Map.entry(OrderStatus.finishing, OrderStage.sanding),
                Map.entry(OrderStatus.qc, OrderStage.sanding),
                Map.entry(OrderStatus.packed, OrderStage.sanding),
                Map.entry(OrderStatus.shipped, OrderStage.shipped),
                Map.entry(OrderStatus.delivered, OrderStage.delivered),
                Map.entry(OrderStatus.cancelled, OrderStage.cancelled));

        assertThat(expected).hasSize(OrderStatus.values().length);
        expected.forEach((status, stage) -> assertThat(status.stage()).as(status.name()).isEqualTo(stage));
    }

    @Test
    void onlyDeliveredAndCancelledAreTerminal() {
        assertThat(OrderStatus.values()).filteredOn(OrderStatus::terminal).containsExactlyInAnyOrder(OrderStatus.delivered, OrderStatus.cancelled);
    }

    @Test
    void namesAreTheContractValues() {
        assertThat(OrderStatus.pending_payment.name()).isEqualTo("pending_payment");
        assertThat(OrderStage.sanding.name()).isEqualTo("sanding");
    }
}
