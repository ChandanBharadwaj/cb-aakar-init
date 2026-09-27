package studio.aakar.api.catalog.internal;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import studio.aakar.api.catalog.AdminExperienceDto;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.catalog.CatalogItemInput;
import studio.aakar.api.catalog.EnvironmentDto;
import studio.aakar.api.catalog.ExperienceDto;
import studio.aakar.api.catalog.ExperienceInput;
import studio.aakar.api.catalog.FamilyDto;
import studio.aakar.api.catalog.FamilyInput;
import studio.aakar.api.catalog.HardwareItemDto;
import studio.aakar.api.catalog.HardwareItemInput;
import studio.aakar.api.catalog.HardwareRef;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.catalog.MaterialInput;
import studio.aakar.api.catalog.ShelfDto;
import studio.aakar.api.pricing.PriceCalculator;
import studio.aakar.api.pricing.PricingPolicy;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.templates.TemplateDescriptor;
import studio.aakar.api.templates.Templates;

@Service
@Transactional(readOnly = true)
class CatalogService implements Catalog {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);

    private final CatalogItemRepository items;
    private final MaterialRepository materials;
    private final ShelfRepository shelves;
    private final TemplateFamilyRepository families;
    private final HardwareItemRepository hardware;
    private final EnvironmentRepository environments;
    private final ExperienceRepository experiences;
    private final Templates templates;
    private final PriceCalculator pricing;
    private final FamilyJson familyJson;
    private final ExperienceJson experienceJson;
    private final Clock clock;

    CatalogService(CatalogItemRepository items, MaterialRepository materials, ShelfRepository shelves, TemplateFamilyRepository families,
            HardwareItemRepository hardware, EnvironmentRepository environments, ExperienceRepository experiences, Templates templates,
            PriceCalculator pricing, FamilyJson familyJson, ExperienceJson experienceJson, Clock clock) {
        this.items = items;
        this.materials = materials;
        this.shelves = shelves;
        this.families = families;
        this.hardware = hardware;
        this.environments = environments;
        this.experiences = experiences;
        this.templates = templates;
        this.pricing = pricing;
        this.familyJson = familyJson;
        this.experienceJson = experienceJson;
        this.clock = clock;
    }

    // ---- Shop items and shelves --------------------------------------------------------------------------------------

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
        validateItem(input);
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
        validateItem(input);
        item.apply(input, clock.instant());
        log.info("Catalog item {} updated", slug);
        return item.toDto();
    }

    @Override
    public List<ShelfDto> shelves() {
        return shelves.findAllByOrderBySortOrderAscIdAsc().stream().map(ShelfEntity::toDto).toList();
    }

    private void validateItem(CatalogItemInput input) {
        requireShelfKnown("category", input.category());
        requireFamilyKnownOrAbsent("family_id", input.familyId());
        requireMaterialKnown(input.defaultMaterial());
        if (input.environment() != null && !input.environment().isBlank()) {
            requireEnvironmentKnown("environment", input.environment());
        }
    }

    // ---- Materials -----------------------------------------------------------------------------------------------------

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

    // ---- Outcome families (Avatars) ------------------------------------------------------------------------------------

    @Override
    public List<FamilyDto> families(String kind, boolean includeUnavailable) {
        String k = blankToNull(kind);
        if (k != null && !FamilyDto.KINDS.contains(k)) {
            throw ApiProblemException.validation("kind must be one of " + String.join(", ", FamilyDto.KINDS) + "; got '" + k + "'");
        }
        FamilyContext context = familyContext();
        return families.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(f -> k == null || k.equals(f.kind()))
                .map(f -> context.toDto(f))
                .filter(f -> includeUnavailable || f.orderable())
                .toList();
    }

    @Override
    public Optional<FamilyDto> family(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return families.findById(id).map(f -> familyContext().toDto(f));
    }

    @Override
    public boolean familyExists(String id) {
        return id != null && !id.isBlank() && families.existsById(id.trim());
    }

    @Override
    public List<HardwareRef> familyHardware(String id) {
        if (id == null || id.isBlank()) {
            return List.of();
        }
        return families.findById(id.trim()).map(family -> {
            Map<String, String> names = new LinkedHashMap<>();
            hardware.findAll().forEach(h -> names.put(h.sku(), h.name()));
            return family.hardware().stream()
                    .map(ref -> familyJson.from(ref, HardwareRef.class))
                    .map(ref -> ref.named(names.get(ref.sku())))
                    .toList();
        }).orElse(List.of());
    }

    @Override
    public List<FamilyDto> allFamilies() {
        return families(null, true);
    }

    @Override
    @Transactional
    public FamilyDto createFamily(FamilyInput input) {
        if (families.existsById(input.id())) {
            throw ApiProblemException.conflict(ProblemCodes.FAMILY_EXISTS, "Family exists",
                    "Family '" + input.id() + "' already exists; update it with PUT /admin/api/families/" + input.id());
        }
        validateFamily(input);
        TemplateFamilyEntity saved = families.save(new TemplateFamilyEntity(input.id(), input, familyJson, clock.instant()));
        log.info("Family {} ({} · {}) created", saved.id(), saved.codename(), saved.name());
        return familyContext().toDto(saved);
    }

    @Override
    @Transactional
    public FamilyDto updateFamily(String id, FamilyInput input) {
        TemplateFamilyEntity family = families.findById(id).orElseThrow(() -> unknownFamily(id));
        if (input.id() != null && !input.id().equals(id)) {
            log.info("Family {} updated with body id {}; the path id wins", id, input.id());
        }
        validateFamily(input);
        family.apply(input, familyJson, clock.instant());
        log.info("Family {} updated (available={})", id, family.available());
        return familyContext().toDto(family);
    }

    private void validateFamily(FamilyInput input) {
        requireShelfKnown("shelf", input.shelf());
        requireEnvironmentKnown("environment", input.environmentOrDefault());
        for (HardwareRef ref : input.hardwareOrEmpty()) {
            if (!hardware.existsById(ref.sku())) {
                throw ApiProblemException.unprocessable(ProblemCodes.UNKNOWN_HARDWARE, "Unknown hardware",
                        "hardware sku '" + ref.sku() + "' is not a known hardware item (see GET /admin/api/hardware)");
            }
        }
        FamilyDto.SizeEnvelope envelope = input.sizeEnvelopeMm();
        if (envelope != null && envelope.minLongestMm() > envelope.maxLongestMm()) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    "size_envelope_mm.min_longest_mm (" + envelope.minLongestMm() + ") must not exceed max_longest_mm (" + envelope.maxLongestMm() + ")");
        }
        List<String> allowed = input.materialRulesOrDefault().allowed();
        if (allowed != null) {
            for (String materialId : allowed) {
                if (!materials.existsById(materialId)) {
                    throw ApiProblemException.unprocessable(ProblemCodes.UNKNOWN_MATERIAL, "Unknown material",
                            "material_rules.allowed names '" + materialId + "', which is not a known material");
                }
            }
        }
    }

    /** Hardware names, the live descriptors grouped by family and the active pricing policy, read once per request. */
    private FamilyContext familyContext() {
        Map<String, String> names = new LinkedHashMap<>();
        hardware.findAll().forEach(h -> names.put(h.sku(), h.name()));
        Map<String, List<TemplateDescriptor>> byFamily = templates.all().stream()
                .filter(d -> d.family() != null)
                .collect(Collectors.groupingBy(TemplateDescriptor::family, LinkedHashMap::new, Collectors.toList()));
        return new FamilyContext(names, byFamily, pricing.policy());
    }

    private final class FamilyContext {

        private final Map<String, String> hardwareNames;
        private final Map<String, List<TemplateDescriptor>> templatesByFamily;
        private final PricingPolicy policy;

        FamilyContext(Map<String, String> hardwareNames, Map<String, List<TemplateDescriptor>> templatesByFamily, PricingPolicy policy) {
            this.hardwareNames = hardwareNames;
            this.templatesByFamily = templatesByFamily;
            this.policy = policy;
        }

        FamilyDto toDto(TemplateFamilyEntity family) {
            // price_from_paise is the family minimum (a floor), until prices are computed per live template and finish
            Long priceFrom = PriceCalculator.minimumSubtotal(policy, family.id()).orElse(null);
            return familyJson.toDto(family, hardwareNames, templatesByFamily.getOrDefault(family.id(), List.of()), priceFrom);
        }
    }

    static ApiProblemException unknownFamily(String id) {
        return ApiProblemException.notFound(ProblemCodes.UNKNOWN_FAMILY, "Unknown family", "Family " + id + " was not found");
    }

    // ---- Bought-in hardware --------------------------------------------------------------------------------------------

    @Override
    public List<HardwareItemDto> hardware() {
        return hardware.findAllByOrderBySkuAsc().stream().map(HardwareItemEntity::toDto).toList();
    }

    @Override
    public Optional<HardwareItemDto> hardwareItem(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        return hardware.findById(sku).map(HardwareItemEntity::toDto);
    }

    @Override
    @Transactional
    public HardwareItemDto createHardware(HardwareItemInput input) {
        if (hardware.existsById(input.sku())) {
            throw ApiProblemException.conflict(ProblemCodes.HARDWARE_EXISTS, "Hardware exists",
                    "Hardware item '" + input.sku() + "' already exists; update it with PUT /admin/api/hardware/" + input.sku());
        }
        Instant now = clock.instant();
        HardwareItemDto created = hardware.save(new HardwareItemEntity(input, now)).toDto();
        log.info("Hardware item {} created", created.sku());
        return created;
    }

    @Override
    @Transactional
    public HardwareItemDto updateHardware(String sku, HardwareItemInput input) {
        HardwareItemEntity item = hardware.findById(sku).orElseThrow(() -> ApiProblemException.notFound("Hardware item", sku));
        if (input.sku() != null && !input.sku().equals(sku)) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    "The sku in the body (" + input.sku() + ") must match the path (" + sku + ")");
        }
        item.apply(input, clock.instant());
        log.info("Hardware item {} updated (available={})", sku, input.availableOrDefault());
        return item.toDto();
    }

    // ---- Viewer environments (Mahaul) and experiences (Duniya) ---------------------------------------------------------

    @Override
    public List<EnvironmentDto> environments() {
        return environments.findAllByOrderBySortOrderAscIdAsc().stream().map(EnvironmentEntity::toDto).toList();
    }

    @Override
    public List<ExperienceDto> experiences(boolean includeUnavailable) {
        List<ExperienceEntity> rows = experiences.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(x -> includeUnavailable || x.available())
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        ExperienceContext context = experienceContext();
        return rows.stream().map(context::toDto).toList();
    }

    @Override
    public Optional<ExperienceDto> experience(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return experiences.findBySlug(slug.trim()).map(x -> experienceContext().toDto(x));
    }

    @Override
    public boolean experienceExists(String id) {
        return id != null && !id.isBlank() && experiences.existsById(id.trim());
    }

    @Override
    public List<AdminExperienceDto> allExperiences() {
        return experiences.findAllByOrderBySortOrderAscIdAsc().stream().map(experienceJson::toAdminDto).toList();
    }

    @Override
    public Optional<AdminExperienceDto> adminExperience(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return experiences.findById(id.trim()).map(experienceJson::toAdminDto);
    }

    @Override
    @Transactional
    public AdminExperienceDto createExperience(ExperienceInput input) {
        if (experiences.existsById(input.id())) {
            throw ApiProblemException.conflict(ProblemCodes.EXPERIENCE_EXISTS, "Experience exists",
                    "Experience '" + input.id() + "' already exists; update it with PUT /admin/api/experiences/" + input.id());
        }
        if (experiences.existsBySlug(input.slug())) {
            throw slugTaken(input.slug());
        }
        validateExperience(input);
        ExperienceEntity saved = experiences.save(new ExperienceEntity(input.id(), input, experienceJson, clock.instant()));
        log.info("Experience {} ({} · {}) created at /duniya/{}", saved.id(), saved.codename(), saved.title(), saved.slug());
        return experienceJson.toAdminDto(saved);
    }

    @Override
    @Transactional
    public AdminExperienceDto updateExperience(String id, ExperienceInput input) {
        ExperienceEntity experience = experiences.findById(id).orElseThrow(() -> unknownExperience(id));
        if (input.id() != null && !input.id().equals(id)) {
            log.info("Experience {} updated with body id {}; the path id wins", id, input.id());
        }
        if (experiences.existsBySlugAndIdNot(input.slug(), id)) {
            throw slugTaken(input.slug());
        }
        validateExperience(input);
        experience.apply(input, experienceJson, clock.instant());
        log.info("Experience {} updated (available={}, slug {})", id, experience.available(), experience.slug());
        return experienceJson.toAdminDto(experience);
    }

    /** What the schema cannot say: references exist, lists hold no repeats, season windows are real and in order. */
    private void validateExperience(ExperienceInput input) {
        requireEnvironmentKnown("environment", input.environment());
        requireDistinct("avatars", input.avatars());
        for (String familyId : input.avatars()) {
            if (!families.existsById(familyId.trim())) {
                throw ApiProblemException.unprocessable(ProblemCodes.UNKNOWN_FAMILY, "Unknown family",
                        "avatars names '" + familyId + "', which is not a family (see GET /admin/api/families)");
            }
        }
        requireDistinct("items", input.itemsOrEmpty());
        for (String slug : input.itemsOrEmpty()) {
            if (!items.existsById(slug.trim())) {
                throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                        "items names '" + slug + "', which is not a Shop item (see GET /admin/api/catalog/items)");
            }
        }
        requireDistinct("motif_pack", input.motifPackOrEmpty());
        requireDistinct("collections", input.collectionsOrEmpty().stream().map(ExperienceDto.Collection::id).toList());
        List<ExperienceDto.SeasonWindow> season = input.seasonOrEmpty();
        for (int i = 0; i < season.size(); i++) {
            requireSeasonWindow(i, season.get(i));
        }
    }

    /**
     * Both ends calendar dates with {@code starts_on ≤ ends_on}, or both month-days (a recurring window, which may wrap the
     * new year); every date must exist ({@code --02-29} does, {@code --02-30} does not).
     */
    private static void requireSeasonWindow(int index, ExperienceDto.SeasonWindow window) {
        String where = "season[" + index + "] (" + window.label() + ")";
        boolean startsRecurring = window.startsOn().startsWith("--");
        if (startsRecurring != window.endsOn().startsWith("--")) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    where + " mixes a date and a month-day; use two dates (2026-10-20) or two month-days (--10-01)");
        }
        try {
            if (startsRecurring) {
                MonthDay.parse(window.startsOn());
                MonthDay.parse(window.endsOn());
            } else if (LocalDate.parse(window.startsOn()).isAfter(LocalDate.parse(window.endsOn()))) {
                throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                        where + " ends (" + window.endsOn() + ") before it starts (" + window.startsOn() + ")");
            }
        } catch (DateTimeException e) {
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    where + " names a day that does not exist (" + window.startsOn() + " to " + window.endsOn() + ")");
        }
    }

    private static void requireDistinct(String field, List<String> values) {
        Set<String> seen = new HashSet<>();
        for (String value : values) {
            if (value != null && !seen.add(value.trim())) {
                throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                        field + " lists '" + value.trim() + "' more than once");
            }
        }
    }

    /** Every family row, the family view context and every Shop item, read once per request. */
    private ExperienceContext experienceContext() {
        Map<String, TemplateFamilyEntity> familyRows = families.findAll().stream()
                .collect(Collectors.toMap(TemplateFamilyEntity::id, Function.identity()));
        Map<String, CatalogItemDto> itemsBySlug = items.findAll().stream()
                .map(CatalogItemEntity::toDto)
                .collect(Collectors.toMap(CatalogItemDto::slug, Function.identity()));
        return new ExperienceContext(familyContext(), familyRows, itemsBySlug);
    }

    private final class ExperienceContext {

        private final FamilyContext familyContext;
        private final Map<String, TemplateFamilyEntity> familyRows;
        private final Map<String, CatalogItemDto> itemsBySlug;
        private final Map<String, FamilyDto> familyViews = new LinkedHashMap<>();

        ExperienceContext(FamilyContext familyContext, Map<String, TemplateFamilyEntity> familyRows, Map<String, CatalogItemDto> itemsBySlug) {
            this.familyContext = familyContext;
            this.familyRows = familyRows;
            this.itemsBySlug = itemsBySlug;
        }

        /** The orderable avatars in the experience's order, its curated items in order and the lowest floor among the avatars. */
        ExperienceDto toDto(ExperienceEntity experience) {
            List<FamilyDto> avatars = experience.avatarIds().stream()
                    .map(this::familyView)
                    .filter(Objects::nonNull)
                    .filter(FamilyDto::orderable)
                    .toList();
            List<CatalogItemDto> curated = experience.itemSlugs().stream().map(itemsBySlug::get).filter(Objects::nonNull).toList();
            Long priceFrom = avatars.stream().map(FamilyDto::priceFromPaise).filter(Objects::nonNull).min(Long::compare).orElse(null);
            return experienceJson.toDto(experience, avatars, curated, priceFrom);
        }

        private FamilyDto familyView(String familyId) {
            TemplateFamilyEntity row = familyRows.get(familyId);
            return row == null ? null : familyViews.computeIfAbsent(familyId, id -> familyContext.toDto(row));
        }
    }

    static ApiProblemException unknownExperience(String idOrSlug) {
        return ApiProblemException.notFound(ProblemCodes.UNKNOWN_EXPERIENCE, "Unknown experience", "Experience " + idOrSlug + " was not found");
    }

    private static ApiProblemException slugTaken(String slug) {
        return ApiProblemException.conflict(ProblemCodes.SLUG_EXISTS, "Slug exists", "Another experience already lives at /duniya/" + slug);
    }

    // ---- Shared checks -------------------------------------------------------------------------------------------------

    /** The backdrop must be in {@code environments}; 422 {@code validation_failed} naming the known ids otherwise. */
    private void requireEnvironmentKnown(String field, String environmentId) {
        if (environmentId == null || environmentId.isBlank() || !environments.existsById(environmentId.trim())) {
            String ids = environments.findAllByOrderBySortOrderAscIdAsc().stream().map(EnvironmentEntity::id).collect(Collectors.joining(", "));
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    field + " '" + environmentId + "' is not a backdrop; the backdrops are: " + ids);
        }
    }

    private void requireShelfKnown(String field, String shelfId) {
        if (shelfId == null || shelfId.isBlank() || !shelves.existsById(shelfId.trim())) {
            String ids = shelves.findAllByOrderBySortOrderAscIdAsc().stream().map(ShelfEntity::id).collect(Collectors.joining(", "));
            throw ApiProblemException.unprocessable(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    field + " '" + shelfId + "' is not a shelf; the shelves are: " + ids);
        }
    }

    private void requireFamilyKnownOrAbsent(String field, String familyId) {
        if (familyId != null && !familyId.isBlank() && !families.existsById(familyId.trim())) {
            throw ApiProblemException.unprocessable(ProblemCodes.UNKNOWN_FAMILY, "Unknown family",
                    field + " '" + familyId + "' is not a known family (see GET /admin/api/families)");
        }
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
                || contains(item.slug(), needle) || contains(item.category(), needle) || contains(item.familyId(), needle);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
