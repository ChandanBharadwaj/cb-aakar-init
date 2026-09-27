package studio.aakar.api.payment;

import java.util.UUID;

/** Published inside the confirming transaction when a payment fails; the order stays awaiting payment. */
public record PaymentFailed(UUID paymentId, UUID orderId, UUID userId) {
}
