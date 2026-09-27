package studio.aakar.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A viewer backdrop (Mahaul): {@code Environment} in both contracts, the {@code experience.v1.json} environment row. Every
 * {@code environment} field (experiences, families, template descriptors, Shop items) names one. {@code presetKey} is the
 * storefront viewer preset that renders it (presets are code); {@code palette} holds swatches, the backdrop colour first.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnvironmentDto(String id, String label, String surface, String presetKey, List<String> palette, int sortOrder) {

    public EnvironmentDto {
        palette = palette == null ? null : List.copyOf(palette);
    }
}
