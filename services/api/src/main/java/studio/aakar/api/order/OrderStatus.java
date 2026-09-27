package studio.aakar.api.order;

/**
 * Internal order status (PLAN §11.1); the customer sees the collapsed {@link OrderStage}. Lowercase
 * constants: contract enum values and DB values.
 */
public enum OrderStatus {
    pending_payment(OrderStage.payment),
    confirmed(OrderStage.queued),
    queued(OrderStage.queued),
    on_hold(OrderStage.queued),
    slicing(OrderStage.slicing),
    printing(OrderStage.printing),
    reprint(OrderStage.printing),
    finishing(OrderStage.sanding),
    qc(OrderStage.sanding),
    packed(OrderStage.sanding),
    shipped(OrderStage.shipped),
    delivered(OrderStage.delivered),
    cancelled(OrderStage.cancelled);

    private final OrderStage stage;

    OrderStatus(OrderStage stage) {
        this.stage = stage;
    }

    public OrderStage stage() {
        return stage;
    }

    /** Nothing follows; the tracking stream closes. */
    public boolean terminal() {
        return this == delivered || this == cancelled;
    }
}
