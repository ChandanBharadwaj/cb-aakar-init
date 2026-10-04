package studio.aakar.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import studio.aakar.api.templates.TemplateDescriptor;

/**
 * An outcome family (Avatar): {@code Family} in the storefront contract and {@code AdminFamily} in the management
 * contract, i.e. the {@code template-family.v1.json} row plus read-only state. {@code ready} is true when at least
 * one live template of the family exists in the geometry service; {@code templates} are those descriptors (the
 * storefront picker) and {@code templateIds} their ids (the portal). {@code priceFromPaise} is the family's minimum
 * subtotal under the active pricing policy when it sets one: a floor for "from ₹249" copy, not a quote. Brand copy
 * ({@code codename}, {@code name}, {@code tagline}) is data; the ids never change. Nulls are left out so the JSON
 * validates against the schemas.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FamilyDto(
        String id,
        String codename,
        String name,
        String tagline,
        String description,
        String kind,
        String tier,
        String shelf,
        Integer demandRank,
        String defaultTemplateId,
        String environment,
        SizeEnvelope sizeEnvelopeMm,
        List<HardwareRef> hardware,
        MaterialRules materialRules,
        String shapeTolerance,
        ContentSlot contentSlot,
        boolean available,
        int sortOrder,
        boolean ready,
        List<TemplateDescriptor> templates,
        List<String> templateIds,
        Long priceFromPaise,
        Instant updatedAt) {

    public static final String KIND_CARRIER = "carrier";
    public static final String KIND_OBJECT = "object";
    public static final String KIND_RAW = "raw";
    public static final List<String> KINDS = List.of(KIND_CARRIER, KIND_OBJECT, KIND_RAW);
    public static final List<String> TIERS = List.of("launch", "next", "later");
    public static final List<String> SHAPE_TOLERANCES = List.of("any", "constrained", "strict");
    /** The Chhaap feature types ({@code design-spec.v1.json} feature kinds). */
    public static final List<String> FEATURE_TYPES = List.of("emboss_text", "motif", "relief_image", "hero_mesh");

    public FamilyDto {
        hardware = hardware == null ? List.of() : List.copyOf(hardware);
        templates = templates == null ? List.of() : List.copyOf(templates);
        templateIds = templateIds == null ? List.of() : List.copyOf(templateIds);
    }

    /** Available to customers and backed by a live template: what {@code GET /api/families} lists. */
    public boolean orderable() {
        return available && ready;
    }

    /** The row without the (large, derived) template descriptors: what the audit log keeps as before/after. */
    public FamilyDto withoutTemplates() {
        return new FamilyDto(id, codename, name, tagline, description, kind, tier, shelf, demandRank, defaultTemplateId, environment,
                sizeEnvelopeMm, hardware, materialRules, shapeTolerance, contentSlot, available, sortOrder, ready, List.of(), templateIds,
                priceFromPaise, updatedAt);
    }

    /** {@code size_envelope_mm}: the longest dimension a piece of this family may have; validates raw-print sizes and drives copy. */
    public record SizeEnvelope(
            @NotNull(message = "size_envelope_mm.min_longest_mm is required")
            @Positive(message = "size_envelope_mm.min_longest_mm must be positive") Double minLongestMm,
            @NotNull(message = "size_envelope_mm.max_longest_mm is required")
            @Positive(message = "size_envelope_mm.max_longest_mm must be positive") Double maxLongestMm) {
    }

    /**
     * {@code material_rules}: {@code allowed} lists digital material ids, {@code null} meaning every available material;
     * {@code heat_safe_only} keeps coasters and lamps in heat-safe filament.
     */
    public record MaterialRules(
            boolean heatSafeOnly,
            List<@Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "material_rules.allowed entries must be material ids") String> allowed,
            List<@Pattern(regexp = "^(matte|silk)$", message = "material_rules.excluded_finish_classes entries must be matte or silk") String>
                    excludedFinishClasses) {

        public static final MaterialRules NONE = new MaterialRules(false, null, List.of());

        public MaterialRules {
            allowed = allowed == null ? null : List.copyOf(allowed);
            excludedFinishClasses = excludedFinishClasses == null ? List.of() : List.copyOf(excludedFinishClasses);
        }
    }

    /**
     * {@code content_slot}, the Chhaap: which feature types a family accepts, the anchors the UI offers first, whether it
     * carries a customer's own 3D form ({@code hero_volume}) and the text length cap. A template's
     * {@code features_supported} must be a subset of {@code accepts}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContentSlot(
            @NotNull(message = "content_slot.accepts is required")
            List<@Pattern(regexp = "^(emboss_text|motif|relief_image|hero_mesh)$",
                    message = "content_slot.accepts entries must be emboss_text, motif, relief_image or hero_mesh") String> accepts,
            List<String> anchors,
            boolean heroVolume,
            @Min(value = 1, message = "content_slot.max_text_chars must be 1-40")
            @Max(value = 40, message = "content_slot.max_text_chars must be 1-40") Integer maxTextChars) {

        public ContentSlot {
            accepts = accepts == null ? List.of() : List.copyOf(accepts);
            anchors = anchors == null ? List.of() : List.copyOf(anchors);
        }
    }
}
