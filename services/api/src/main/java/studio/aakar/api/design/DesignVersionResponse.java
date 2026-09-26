package studio.aakar.api.design;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import studio.aakar.api.pricing.PriceBreakdown;

/** {@code DesignVersion} in the OpenAPI document. JSON documents are served exactly as stored. */
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
        String karigarNote,
        UUID jobId,
        String createdBy,
        Instant createdAt) {
}
