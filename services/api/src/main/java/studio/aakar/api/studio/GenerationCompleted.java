package studio.aakar.api.studio;

import java.util.UUID;

/**
 * Application event published (synchronously, inside the studio's transaction) when a job's
 * {@code design.completed} result is applied. The design module marks the version ready from it.
 */
public record GenerationCompleted(UUID jobId, UUID designId, UUID versionId, DesignCompletedPayload payload) {
}
