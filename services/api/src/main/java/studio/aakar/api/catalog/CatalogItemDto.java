package studio.aakar.api.catalog;

import java.util.List;
import java.util.Map;

/** A Shop item as served by {@code GET /api/catalog/items}. Money is integer paise. */
public record CatalogItemDto(
        String slug,
        String name,
        String category,
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
