package studio.aakar.api.studio;

import java.time.Instant;
import java.util.UUID;

/** {@code JobStageEvent} in the OpenAPI document: the {@code data} of every SSE {@code stage} event. */
public record JobStageEvent(
        UUID jobId,
        int sequence,
        JobStage stage,
        String message,
        Integer percent,
        UUID versionId,
        String errorCode,
        Instant at) {
}
