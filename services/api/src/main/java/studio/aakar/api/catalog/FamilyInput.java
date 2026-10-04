package studio.aakar.api.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * {@code AdminFamilyInput} in the management contract: the {@code template-family.v1.json} family row. Referenced
 * shelves, environments (backdrops, {@code studio} when absent), hardware SKUs and allowed materials are checked by the
 * catalog service (422), envelopes must have {@code min ≤ max}.
 */
public record FamilyInput(
        @NotBlank(message = "id is required")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "id must be snake_case starting with a letter")
        @Size(max = 40, message = "id must be at most 40 characters") String id,
        @NotBlank(message = "codename is required") @Size(max = 40, message = "codename must be at most 40 characters") String codename,
        @NotBlank(message = "name is required") @Size(max = 80, message = "name must be at most 80 characters") String name,
        @Size(max = 120, message = "tagline must be at most 120 characters") String tagline,
        @Size(max = 500, message = "description must be at most 500 characters") String description,
        @NotBlank(message = "kind is required") @Pattern(regexp = "^(carrier|object|raw|hybrid)$", message = "kind must be carrier, object, raw or hybrid") String kind,
        @NotBlank(message = "tier is required") @Pattern(regexp = "^(launch|next|later)$", message = "tier must be launch, next or later") String tier,
        @NotBlank(message = "shelf is required") @Size(max = 40, message = "shelf must be at most 40 characters") String shelf,
        @Min(value = 1, message = "demand_rank must be at least 1") Integer demandRank,
        @NotBlank(message = "default_template_id is required")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "default_template_id must be snake_case starting with a letter")
        @Size(max = 80, message = "default_template_id must be at most 80 characters") String defaultTemplateId,
        @Size(max = 40, message = "environment must be at most 40 characters") String environment,
        @Valid FamilyDto.SizeEnvelope sizeEnvelopeMm,
        List<@Valid HardwareRef> hardware,
        @Valid FamilyDto.MaterialRules materialRules,
        @NotBlank(message = "shape_tolerance is required")
        @Pattern(regexp = "^(any|constrained|strict)$", message = "shape_tolerance must be any, constrained or strict") String shapeTolerance,
        @NotNull(message = "content_slot is required") @Valid FamilyDto.ContentSlot contentSlot,
        @NotNull(message = "available is required") Boolean available,
        Integer sortOrder) {

    public static final String DEFAULT_ENVIRONMENT = "studio";
    public static final int DEFAULT_SORT_ORDER = 100;

    public String environmentOrDefault() {
        return environment == null || environment.isBlank() ? DEFAULT_ENVIRONMENT : environment.trim();
    }

    public int sortOrderOrDefault() {
        return sortOrder == null ? DEFAULT_SORT_ORDER : sortOrder;
    }

    public List<HardwareRef> hardwareOrEmpty() {
        return hardware == null ? List.of() : hardware;
    }

    public FamilyDto.MaterialRules materialRulesOrDefault() {
        return materialRules == null ? FamilyDto.MaterialRules.NONE : materialRules;
    }
}
