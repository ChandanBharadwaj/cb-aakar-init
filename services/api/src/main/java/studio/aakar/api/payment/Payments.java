package studio.aakar.api.payment;

import java.util.Optional;
import java.util.UUID;

/** Public API of the payment module. */
public interface Payments {

    /**
     * Starts a payment attempt for an order. Any earlier attempt of the order still {@code created}/{@code pending}
     * is marked {@code failed} (abandoned) so only one attempt can succeed.
     */
    PaymentDto create(UUID orderId, String orderNumber, UUID userId, long amountPaise);

    Optional<PaymentDto> find(UUID paymentId);

    /** The payment only when it was made by {@code userId}. */
    Optional<PaymentDto> findForUser(UUID paymentId, UUID userId);

    /** The most recent attempt for an order. */
    Optional<PaymentDto> latestForOrder(UUID orderId);

    /**
     * Records the outcome — what a gateway webhook handler calls. Success mints an invoice number and publishes
     * {@link PaymentSucceeded}; failure publishes {@link PaymentFailed}. 409 {@code payment_final} when already final.
     *
     * @param method {@code upi}, {@code card}, {@code netbanking} … as the gateway reports it
     * @param gatewayRef the provider's payment id, or null to keep the one from creation
     */
    PaymentDto confirm(UUID paymentId, PaymentOutcome outcome, String method, String gatewayRef);
}
