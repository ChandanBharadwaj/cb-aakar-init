package studio.aakar.api.catalog.internal;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.catalog.CatalogItemInput;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.catalog.MaterialInput;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;

@Service
@Transactional(readOnly = true)
class CatalogService implements Catalog {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CatalogItemRepository items;
    private final MaterialRepository materials;
    private final Clock clock;

    CatalogService(CatalogItemRepository items, MaterialRepository materials, Clock clock) {
        this.items = items;
        this.materials = materials;
        this.clock = clock;
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
    @Transactional
    public CatalogItemDto createItem(CatalogItemInput input) {
        if (items.existsById(input.slug())) {
            throw ApiProblemException.conflict(ProblemCodes.SLUG_EXISTS, "Slug exists",
                    "A catalog item with slug '" + input.slug() + "' already exists");
        }
        requireMaterialKnown(input.defaultMaterial());
        CatalogItemDto created = items.save(new CatalogItemEntity(input, clock.instant())).toDto();
        log.info("Catalog item {} created", created.slug());
        return created;
    }

    @Override
    @Transactional
    public CatalogItemDto updateItem(String slug, CatalogItemInput input) {
        CatalogItemEntity item = items.findById(slug).orElseThrow(() -> ApiProblemException.notFound("Catalog item", slug));
        if (input.slug() != null && !input.slug().equals(slug)) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    "The slug in the body (" + input.slug() + ") must match the path (" + slug + ")");
        }
        requireMaterialKnown(input.defaultMaterial());
        item.apply(input, clock.instant());
        log.info("Catalog item {} updated", slug);
        return item.toDto();
    }

    @Override
    public List<MaterialDto> materials() {
        return materials.findByAvailableTrueOrderBySortOrderAscIdAsc().stream().map(MaterialEntity::toDto).toList();
    }

    @Override
    public List<MaterialDto> allMaterials() {
        return materials.findAllByOrderBySortOrderAscIdAsc().stream().map(MaterialEntity::toDto).toList();
    }

    @Override
    public Optional<MaterialDto> material(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return materials.findById(id).map(MaterialEntity::toDto);
    }

    @Override
    @Transactional
    public MaterialDto createMaterial(MaterialInput input) {
        if (materials.existsById(input.id())) {
            throw ApiProblemException.conflict(ProblemCodes.MATERIAL_EXISTS, "Material exists",
                    "Material '" + input.id() + "' already exists");
        }
        validatePbr(input.pbr());
        MaterialDto created = materials.save(new MaterialEntity(input, clock.instant())).toDto();
        log.info("Material {} created", created.id());
        return created;
    }

    @Override
    @Transactional
    public MaterialDto updateMaterial(String id, MaterialInput input) {
        MaterialEntity material = materials.findById(id).orElseThrow(() -> ApiProblemException.notFound("Material", id));
        if (input.id() != null && !input.id().equals(id)) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    "The id in the body (" + input.id() + ") must match the path (" + id + ")");
        }
        validatePbr(input.pbr());
        material.apply(input, clock.instant());
        log.info("Material {} updated (available={})", id, input.availableOrDefault());
        return material.toDto();
    }

    private void requireMaterialKnown(String materialId) {
        if (materialId == null || !materials.existsById(materialId)) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, ProblemCodes.UNKNOWN_MATERIAL, "Unknown material",
                    "default_material '" + materialId + "' is not a known material");
        }
    }

    /** {@code pbr} needs at least {@code color}, {@code roughness} and {@code metalness} (the contract's required keys). */
    static void validatePbr(Map<String, Object> pbr) {
        if (pbr == null || !(pbr.get("color") instanceof String color) || !color.matches("^#[0-9A-Fa-f]{6}$")
                || !(pbr.get("roughness") instanceof Number) || !(pbr.get("metalness") instanceof Number)) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    "pbr needs color (#RRGGBB), roughness and metalness");
        }
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
