package studio.aakar.api.design;

import java.time.Instant;
import java.util.UUID;

/** {@code Design} in the OpenAPI document: header plus its latest version. */
public record DesignResponse(
        UUID id,
        DesignSource source,
        String catalogItemSlug,
        String title,
        VersionStatus status,
        Instant createdAt,
        int versionsCount,
        DesignVersionResponse latestVersion) {
}
