package studio.aakar.api.studio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.UUID;

/**
 * {@code events/design.completed.v1.json}; also the 200 body of {@code POST /v1/build}. The JSON
 * documents (template, spec, assets, printability, print estimate) are kept as maps so they are stored
 * and served verbatim.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DesignCompletedPayload(
        UUID jobId,
        UUID designId,
        int versionNo,
        Map<String, Object> template,
        Map<String, Object> spec,
        Map<String, Object> assets,
        Map<String, Object> printability,
        Map<String, Object> printEstimate,
        String karigarNote,
        Long buildMs) {

    /** {@code printability.geometry}: bounds_mm, volume_cm3, surface_cm2, triangles. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> geometry() {
        if (printability == null) {
            return null;
        }
        Object geometry = printability.get("geometry");
        return geometry instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }
}
