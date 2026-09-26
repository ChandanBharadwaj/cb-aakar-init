package studio.aakar.api.studio;

import java.util.UUID;

/** Application event published when a job fails; the design module marks the version failed. */
public record GenerationFailed(UUID jobId, UUID designId, UUID versionId, String code, String message) {
}
