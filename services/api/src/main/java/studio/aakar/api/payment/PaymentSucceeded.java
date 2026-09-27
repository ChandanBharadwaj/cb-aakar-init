package studio.aakar.api.payment;

import java.util.UUID;

/**
 * Published synchronously inside the confirming transaction when a payment succeeds. The order module
 * confirms and queues the order; the cart module empties the customer's cart.
 */
public record PaymentSucceeded(UUID paymentId, UUID orderId, UUID userId, long amountPaise, String invoiceNumber, String method) {
}
