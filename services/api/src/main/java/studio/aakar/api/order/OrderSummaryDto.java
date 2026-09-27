package studio.aakar.api.order;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** {@code OrderSummary} in the OpenAPI document. */
public record OrderSummaryDto(
        UUID id,
        String number,
        OrderStatus status,
        OrderStage stage,
        String title,
        long totalPaise,
        int itemsCount,
        LocalDate eta,
        Instant placedAt) {
}
