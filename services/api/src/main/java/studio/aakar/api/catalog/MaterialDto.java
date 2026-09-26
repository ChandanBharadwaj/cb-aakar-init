package studio.aakar.api.catalog;

import java.util.Map;

/** A digital material (rendering preset + filament + pricing inputs). */
public record MaterialDto(
        String id,
        String name,
        String filament,
        double densityGCm3,
        String finishClass,
        long ratePerGPaise,
        boolean heatSafe,
        Map<String, Object> pbr) {
}
