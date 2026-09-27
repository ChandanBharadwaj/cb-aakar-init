package studio.aakar.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** {@code AdminMaterialInput} in the management contract. */
public record MaterialInput(
        @NotBlank(message = "id is required")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "id must be snake_case starting with a letter")
        @Size(max = 80) String id,
        @NotBlank(message = "name is required") @Size(max = 40, message = "name must be at most 40 characters") String name,
        @NotBlank(message = "filament is required") @Size(max = 80, message = "filament must be at most 80 characters") String filament,
        @JsonProperty("density_g_cm3")
        @NotNull(message = "density_g_cm3 is required")
        @DecimalMin(value = "0.5", message = "density_g_cm3 must be between 0.5 and 3")
        @DecimalMax(value = "3", message = "density_g_cm3 must be between 0.5 and 3") Double densityGCm3,
        @NotBlank(message = "finish_class is required")
        @Pattern(regexp = "^(matte|silk)$", message = "finish_class must be matte or silk") String finishClass,
        @JsonProperty("rate_per_g_paise")
        @NotNull(message = "rate_per_g_paise is required") @Min(value = 0, message = "rate_per_g_paise must be at least 0") Long ratePerGPaise,
        Boolean heatSafe,
        Boolean available,
        Integer sortOrder,
        @NotNull(message = "pbr is required") Map<String, Object> pbr) {

    public boolean heatSafeOrDefault() {
        return Boolean.TRUE.equals(heatSafe);
    }

    public boolean availableOrDefault() {
        return available == null || available;
    }

    public int sortOrderOrDefault() {
        return sortOrder == null ? 100 : sortOrder;
    }
}
