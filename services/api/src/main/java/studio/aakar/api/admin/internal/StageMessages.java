package studio.aakar.api.admin.internal;

import studio.aakar.api.order.OrderStatus;

/** The tracking-board line staff advance an order with when they leave {@code message} blank. */
final class StageMessages {

    private StageMessages() {
    }

    static String defaultFor(OrderStatus status) {
        return switch (status) {
            case pending_payment -> "Awaiting payment";
            case confirmed -> "Order confirmed";
            case queued -> "Queued at studio";
            case slicing -> "Slicing your piece";
            case printing -> "Printing";
            case finishing -> "Hand sanding & sealing";
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
