package studio.aakar.api.cart;

import java.util.UUID;
import studio.aakar.api.pricing.PriceBreakdown;

/** {@code CartItem} in the OpenAPI document. {@code line_total_paise} is the unit subtotal (before shipping) × qty. */
public record CartItemDto(
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
        String thumbnailUrl,
        boolean purchasable,
        boolean repriced) {
}
