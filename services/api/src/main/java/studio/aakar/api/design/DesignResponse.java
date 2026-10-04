package studio.aakar.api.design;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code Design} in the OpenAPI document: header plus its latest version. {@code familyId} is its outcome family (Avatar),
 * when known; {@code experienceId} the Duniya experience it started from, when it named one.
 */
public record DesignResponse(
        UUID id,
        DesignSource source,
        String catalogItemSlug,
        String familyId,
        String experienceId,
        String title,
        VersionStatus status,
        Instant createdAt,
        int versionsCount,
        DesignVersionResponse latestVersion) {
}
