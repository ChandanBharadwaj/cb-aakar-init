package studio.aakar.api.catalog;

import java.util.List;
import java.util.Map;

/**
 * A Shop item as served by {@code GET /api/catalog/items}. {@code category} is a shelf id ({@code GET /api/catalog/shelves}),
 * {@code familyId} the outcome family (Avatar) of the item's template when known. Money is integer paise.
 */
public record CatalogItemDto(
        String slug,
        String name,
        String category,
        String familyId,
        String description,
        String templateId,
        Map<String, Object> defaultParams,
        String defaultMaterial,
        long basePricePaise,
        String specsLine,
        String environment,
        boolean available,
        List<Map<String, Object>> media) {
}
