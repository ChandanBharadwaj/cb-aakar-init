package studio.aakar.api.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * {@code CatalogItemInput} in the management contract. {@code category} must be a shelf id and {@code family_id}, when
 * given, a seeded family; both are checked by the catalog service (422 {@code validation_failed} / {@code unknown_family}).
 */
public record CatalogItemInput(
        @NotBlank(message = "slug is required")
        @Pattern(regexp = "^[a-z0-9-]{3,60}$", message = "slug must be 3-60 lowercase letters, digits or dashes") String slug,
        @NotBlank(message = "name is required") @Size(max = 80, message = "name must be at most 80 characters") String name,
        @NotBlank(message = "category is required") @Size(max = 40, message = "category must be at most 40 characters") String category,
        @Size(max = 40, message = "family_id must be at most 40 characters") String familyId,
        @Size(max = 500, message = "description must be at most 500 characters") String description,
        @NotBlank(message = "template_id is required") @Size(max = 80) String templateId,
        @NotNull(message = "default_params is required") Map<String, Object> defaultParams,
        @NotBlank(message = "default_material is required") @Size(max = 80) String defaultMaterial,
        @NotNull(message = "base_price_paise is required") @Min(value = 0, message = "base_price_paise must be at least 0") Long basePricePaise,
        @NotNull(message = "specs_line is required") @Size(max = 120, message = "specs_line must be at most 120 characters") String specsLine,
        @Size(max = 40) String environment,
        @NotNull(message = "available is required") Boolean available,
        List<Map<String, Object>> media) {
}
