package studio.aakar.api.design;

import java.time.Instant;
import java.util.UUID;

/** {@code Design} in the OpenAPI document: header plus its latest version. {@code familyId} is its outcome family (Avatar), when known. */
public record DesignResponse(
        UUID id,
        DesignSource source,
        String catalogItemSlug,
        String familyId,
        String title,
        VersionStatus status,
        Instant createdAt,
        int versionsCount,
        DesignVersionResponse latestVersion) {
}
