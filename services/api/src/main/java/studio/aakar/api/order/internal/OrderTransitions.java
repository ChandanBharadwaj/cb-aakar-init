package studio.aakar.api.order.internal;

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

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import studio.aakar.api.order.OrderStatus;

/**
 * PLAN §11.1 state machine:
 * <pre>
 * pending_payment → confirmed → queued → slicing → printing → finishing → qc → packed → shipped → delivered
 *                                    ↘ on_hold (resumes at any production step)   ↘ reprint (back to slicing/printing)
 * cancelled from anything not yet shipped; delivered and cancelled are final.
 * </pre>
 */
final class OrderTransitions {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);
    /** How the portal lists the moves: the production step first, then reprint, hold and cancel. */
    private static final List<OrderStatus> DISPLAY_ORDER = List.of(confirmed, queued, slicing, printing, finishing, qc, packed, shipped,
            delivered, reprint, on_hold, cancelled);

    static {
        ALLOWED.put(pending_payment, EnumSet.of(confirmed, cancelled));
        ALLOWED.put(confirmed, EnumSet.of(queued, cancelled));
        ALLOWED.put(queued, EnumSet.of(slicing, on_hold, cancelled));
        ALLOWED.put(slicing, EnumSet.of(printing, on_hold, cancelled));
        ALLOWED.put(printing, EnumSet.of(finishing, reprint, on_hold, cancelled));
        ALLOWED.put(finishing, EnumSet.of(qc, on_hold, cancelled));
        ALLOWED.put(qc, EnumSet.of(packed, reprint, on_hold, cancelled));
        ALLOWED.put(packed, EnumSet.of(shipped, cancelled));
        ALLOWED.put(shipped, EnumSet.of(delivered));
        ALLOWED.put(delivered, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(cancelled, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(on_hold, EnumSet.of(queued, slicing, printing, finishing, qc, cancelled));
        ALLOWED.put(reprint, EnumSet.of(slicing, printing, on_hold, cancelled));
    }

    private OrderTransitions() {
    }

    static boolean allowed(OrderStatus from, OrderStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    static Set<OrderStatus> next(OrderStatus from) {
        return Set.copyOf(ALLOWED.getOrDefault(from, Set.of()));
    }

    /** {@link #next(OrderStatus)} as the portal shows it, e.g. {@code queued → [slicing, on_hold, cancelled]}. */
    static List<OrderStatus> nextOrdered(OrderStatus from) {
        Set<OrderStatus> allowed = next(from);
        return DISPLAY_ORDER.stream().filter(allowed::contains).toList();
    }

    /** Default tracking-board line for a status. */
    static String defaultMessage(OrderStatus status) {
        return switch (status) {
            case pending_payment -> "Awaiting payment";
            case confirmed -> "Order confirmed · payment received";
            case queued -> "Queued at studio";
            case slicing -> "Slicing your design";
            case printing -> "Printing";
            case finishing -> "Hand finishing";
            case qc -> "Quality check";
            case packed -> "Packed";
            case shipped -> "Shipped";
            case delivered -> "Delivered";
            case cancelled -> "Cancelled";
            case on_hold -> "On hold";
            case reprint -> "Reprinting";
        };
    }
}
