package studio.aakar.api.order;

import java.util.Map;
import java.util.UUID;
import studio.aakar.api.pricing.PriceBreakdown;

/** {@code OrderItem} in the OpenAPI document: a cart line frozen at checkout, with the version's assets. */
public record OrderItemDto(
        UUID id,
        UUID designId,
        UUID versionId,
        Integer versionNo,
        String title,
        String specsLine,
        String materialId,
        String materialName,
        int qty,
        PriceBreakdown unitPrice,
        long lineTotalPaise,
        Map<String, Object> assets) {
}
