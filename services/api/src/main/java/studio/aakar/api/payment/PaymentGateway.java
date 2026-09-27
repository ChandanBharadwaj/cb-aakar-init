package studio.aakar.api.payment;

/** Gateway adapter (Razorpay in production, {@code mock} locally). */
public interface PaymentGateway {

    /** Adapter name stored on every payment, e.g. {@code razorpay} or {@code mock}. */
    String name();

    /** Registers the payment with the provider and returns its reference and where the customer pays. */
    GatewayPayment create(PaymentRequest request);
}
