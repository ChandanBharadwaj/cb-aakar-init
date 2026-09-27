package studio.aakar.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * A digital material (rendering preset + filament + pricing inputs). {@code Material} in the customer contract
 * plus {@code available}, {@code sort_order} and {@code updated_at} for {@code AdminMaterial}. The two names with
 * consecutive capitals are pinned because Jackson's snake_case would otherwise render them as {@code density_gcm3}.
 */
public record MaterialDto(
        String id,
        String name,
        String filament,
        @JsonProperty("density_g_cm3") double densityGCm3,
        String finishClass,
        @JsonProperty("rate_per_g_paise") long ratePerGPaise,
        boolean heatSafe,
        Map<String, Object> pbr,
        boolean available,
        int sortOrder,
        Instant updatedAt) {
}
