package studio.aakar.api.payment;

/** Lowercase constants: contract enum values and DB values. */
public enum PaymentStatus {
    created, pending, succeeded, failed, refunded;

    /** No further confirmation can change it. */
    public boolean isFinal() {
        return this == succeeded || this == failed || this == refunded;
    }
}
