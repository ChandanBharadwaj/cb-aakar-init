package studio.aakar.api.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static studio.aakar.api.order.OrderStatus.cancelled;
import static studio.aakar.api.order.OrderStatus.confirmed;
import static studio.aakar.api.order.OrderStatus.delivered;
import static studio.aakar.api.order.OrderStatus.finishing;
import static studio.aakar.api.order.OrderStatus.on_hold;
import static studio.aakar.api.order.OrderStatus.packed;
import static studio.aakar.api.order.OrderStatus.pending_payment;
import static studio.aakar.api.order.OrderStatus.printing;
import static studio.aakar.api.order.OrderStatus.qc;
import static studio.aakar.api.order.OrderStatus.queued;
import static studio.aakar.api.order.OrderStatus.reprint;
import static studio.aakar.api.order.OrderStatus.shipped;
import static studio.aakar.api.order.OrderStatus.slicing;

import java.util.List;
import org.junit.jupiter.api.Test;
import studio.aakar.api.order.OrderStatus;

/** The PLAN §11.1 state machine as a table. */
class OrderTransitionsTest {

    @Test
    void theHappyPathIsAllowedStepByStep() {
        List<OrderStatus> path = List.of(pending_payment, confirmed, queued, slicing, printing, finishing, qc, packed, shipped, delivered);
        for (int i = 1; i < path.size(); i++) {
            assertThat(OrderTransitions.allowed(path.get(i - 1), path.get(i))).as("%s → %s", path.get(i - 1), path.get(i)).isTrue();
        }
    }

    @Test
    void skippingStepsAndGoingBackwardsIsNot() {
        assertThat(OrderTransitions.allowed(queued, printing)).isFalse();
        assertThat(OrderTransitions.allowed(pending_payment, queued)).isFalse();
        assertThat(OrderTransitions.allowed(printing, slicing)).isFalse();
        assertThat(OrderTransitions.allowed(shipped, packed)).isFalse();
        assertThat(OrderTransitions.allowed(confirmed, confirmed)).isFalse();
    }

    @Test
    void holdReprintAndCancelRules() {
        for (OrderStatus s : List.of(queued, slicing, printing, finishing, qc)) {
            assertThat(OrderTransitions.allowed(s, on_hold)).as("%s → on_hold", s).isTrue();
            assertThat(OrderTransitions.allowed(on_hold, s)).as("on_hold → %s", s).isTrue();
        }
        assertThat(OrderTransitions.allowed(packed, on_hold)).isFalse();
        assertThat(OrderTransitions.allowed(printing, reprint)).isTrue();
        assertThat(OrderTransitions.allowed(qc, reprint)).isTrue();
        assertThat(OrderTransitions.allowed(finishing, reprint)).isFalse();
        assertThat(OrderTransitions.allowed(reprint, slicing)).isTrue();
        assertThat(OrderTransitions.allowed(reprint, printing)).isTrue();
        assertThat(OrderTransitions.allowed(reprint, qc)).isFalse();

        for (OrderStatus s : List.of(pending_payment, confirmed, queued, slicing, printing, finishing, qc, packed, on_hold, reprint)) {
            assertThat(OrderTransitions.allowed(s, cancelled)).as("%s → cancelled", s).isTrue();
        }
        assertThat(OrderTransitions.allowed(shipped, cancelled)).isFalse();
    }

    @Test
    void deliveredAndCancelledAreFinal() {
        assertThat(OrderTransitions.next(delivered)).isEmpty();
        assertThat(OrderTransitions.next(cancelled)).isEmpty();
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(OrderTransitions.allowed(delivered, s)).isFalse();
            assertThat(OrderTransitions.allowed(cancelled, s)).isFalse();
        }
    }

    @Test
    void nullsAreNeverAllowed() {
        assertThat(OrderTransitions.allowed(null, queued)).isFalse();
        assertThat(OrderTransitions.allowed(queued, null)).isFalse();
    }

    @Test
    void everyStatusHasADefaultBoardMessage() {
        for (OrderStatus s : OrderStatus.values()) {
            assertThat(OrderTransitions.defaultMessage(s)).as(s.name()).isNotBlank();
        }
        assertThat(OrderTransitions.defaultMessage(pending_payment)).isEqualTo("Awaiting payment");
        assertThat(OrderTransitions.defaultMessage(queued)).isEqualTo("Queued at studio");
    }
}
