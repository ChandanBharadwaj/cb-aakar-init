package studio.aakar.api.catalog.internal;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.catalog.MaterialDto;

@Service
@Transactional(readOnly = true)
class CatalogService implements Catalog {

    private final CatalogItemRepository items;
    private final MaterialRepository materials;

    CatalogService(CatalogItemRepository items, MaterialRepository materials) {
        this.items = items;
        this.materials = materials;
    }

    @Override
    public List<CatalogItemDto> items(String category, String query) {
        String c = blankToNull(category);
        String q = blankToNull(query);
        // Six SKUs, read-mostly: filter in memory rather than juggling nullable JPQL parameters.
        return items.findAllByOrderByAvailableDescBasePricePaiseAscNameAsc().stream()
                .map(CatalogItemEntity::toDto)
                .filter(i -> c == null || c.equalsIgnoreCase(i.category()))
                .filter(i -> q == null || matches(i, q))
                .toList();
    }

    @Override
    public Optional<CatalogItemDto> item(String slug) {
        return items.findById(slug).map(CatalogItemEntity::toDto);
    }

    @Override
    public List<MaterialDto> materials() {
        return materials.findAllByOrderBySortOrderAscIdAsc().stream().map(MaterialEntity::toDto).toList();
    }

    @Override
    public Optional<MaterialDto> material(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return materials.findById(id).map(MaterialEntity::toDto);
    }

    private static boolean matches(CatalogItemDto item, String q) {
        String needle = q.toLowerCase(Locale.ROOT);
        return contains(item.name(), needle) || contains(item.description(), needle) || contains(item.specsLine(), needle)
                || contains(item.slug(), needle) || contains(item.category(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
