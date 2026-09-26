package studio.aakar.api.catalog.internal;

import java.util.List;
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
        return items.search(c, q).stream().map(CatalogItemEntity::toDto).toList();
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
