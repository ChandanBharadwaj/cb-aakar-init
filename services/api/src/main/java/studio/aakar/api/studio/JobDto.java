package studio.aakar.api.studio;

import java.time.Instant;
import java.util.UUID;

/** {@code Job} in the OpenAPI document. */
public record JobDto(
        UUID id,
        UUID designId,
        UUID versionId,
        int versionNo,
        String type,
        JobStatus status,
        JobStage stage,
        String message,
        String errorCode,
        int attempts,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {
}
