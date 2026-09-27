package studio.aakar.api.design;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import studio.aakar.api.catalog.HardwareRef;
import studio.aakar.api.pricing.PriceBreakdown;

/**
 * {@code DesignVersion} in the OpenAPI document. JSON documents are served exactly as stored. {@code familyId} is the
 * spec's family; {@code hardware} the bought-in parts packed with each piece, with their customer-facing names.
 */
public record DesignVersionResponse(
        UUID id,
        UUID designId,
        int versionNo,
        UUID parentVersionId,
        VersionStatus status,
        Map<String, Object> spec,
        Map<String, Object> template,
        Map<String, Object> assets,
        Map<String, Object> geometry,
        Map<String, Object> printability,
        Map<String, Object> printEstimate,
        PriceBreakdown price,
        String familyId,
        List<HardwareRef> hardware,
        String karigarNote,
        UUID jobId,
        String createdBy,
        Instant createdAt) {

    public DesignVersionResponse {
        hardware = hardware == null ? List.of() : List.copyOf(hardware);
    }

    /** The pre-Avatar form: no family, no hardware. */
    public DesignVersionResponse(UUID id, UUID designId, int versionNo, UUID parentVersionId, VersionStatus status, Map<String, Object> spec,
            Map<String, Object> template, Map<String, Object> assets, Map<String, Object> geometry, Map<String, Object> printability,
            Map<String, Object> printEstimate, PriceBreakdown price, String karigarNote, UUID jobId, String createdBy, Instant createdAt) {
        this(id, designId, versionNo, parentVersionId, status, spec, template, assets, geometry, printability, printEstimate, price, null, List.of(),
                karigarNote, jobId, createdBy, createdAt);
    }

    /** Content features of the spec ({@code design-spec.v1.json#/$defs/feature}), in order; empty when there are none. */
    @JsonIgnore
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> features() {
        Object features = spec == null ? null : spec.get("features");
        if (!(features instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(f -> f instanceof Map<?, ?>).map(f -> (Map<String, Object>) f).toList();
    }
}
