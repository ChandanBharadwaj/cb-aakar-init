package studio.aakar.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * A bought-in hardware item (split ring, magnet, LED base…) with its cost and weight: {@code AdminHardware} in the
 * management contract. {@code name} is customer-facing ("Steel split ring 25 mm") and shows on family cards and
 * packing lists. Money is integer paise; weight in grams.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HardwareItemDto(
        String sku,
        String name,
        long unitCostPaise,
        Double weightG,
        String supplier,
        String url,
        String notes,
        boolean available,
        Instant updatedAt) {
}
