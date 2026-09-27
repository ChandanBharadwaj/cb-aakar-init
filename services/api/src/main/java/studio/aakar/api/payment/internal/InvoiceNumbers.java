package studio.aakar.api.payment.internal;

/** Sequential GST-style invoice numbers: {@code INV-<year>-<6 digits>}, e.g. {@code INV-2026-000001}. */
final class InvoiceNumbers {

    static final String PREFIX = "INV-";

    private InvoiceNumbers() {
    }

    static String format(int year, long sequence) {
        if (sequence < 1) {
            throw new IllegalArgumentException("Invoice sequence must start at 1, got " + sequence);
        }
        return PREFIX + year + "-" + String.format("%06d", sequence);
    }
}
