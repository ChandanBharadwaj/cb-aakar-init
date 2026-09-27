package studio.aakar.api.payment;

import java.util.UUID;

/** A payment attempt to register with the gateway; {@code paymentId} is ours and known before the gateway call. */
public record PaymentRequest(UUID paymentId, UUID orderId, String orderNumber, UUID userId, long amountPaise, String currency) {
}
