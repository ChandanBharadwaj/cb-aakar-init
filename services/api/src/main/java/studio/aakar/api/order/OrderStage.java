package studio.aakar.api.order;

/** Customer-facing collapsed stage (board 06). Lowercase constants: contract enum values and DB values. */
public enum OrderStage {
    payment, queued, slicing, printing, sanding, shipped, delivered, cancelled
}
