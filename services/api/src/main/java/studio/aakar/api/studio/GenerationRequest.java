package studio.aakar.api.studio;

import java.util.Map;
import java.util.UUID;

/** What the design module hands to the studio to start a generation job. */
public record GenerationRequest(UUID designId, UUID versionId, int versionNo, UUID parentVersionId, Map<String, Object> spec) {
}
