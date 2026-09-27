package studio.aakar.api.payment;

import java.time.Instant;
import java.util.UUID;

/** {@code Payment} in the OpenAPI document. */
public record PaymentDto(
        UUID id,
        UUID orderId,
        String gateway,
        String gatewayRef,
        PaymentStatus status,
        String method,
        long amountPaise,
        String currency,
        String payUrl,
        String invoiceNumber,
        Instant createdAt,
        Instant finishedAt) {
}
