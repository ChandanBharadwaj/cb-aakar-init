package studio.aakar.api.order;

/** Customer-facing order numbers (ADR-0007): {@code AK-} + the sequence zero-padded to 6 digits, e.g. {@code AK-000001}. */
public final class OrderNumbers {

    public static final String PREFIX = "AK-";

    private OrderNumbers() {
    }

    public static String format(long sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("Order sequence must start at 1, got " + sequence);
        }
        return PREFIX + String.format("%06d", sequence);
    }
}
