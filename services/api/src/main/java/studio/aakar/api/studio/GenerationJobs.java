package studio.aakar.api.studio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Public API of the studio module. */
public interface GenerationJobs {

    /**
     * Creates a queued job for the version and schedules its dispatch for after the surrounding
     * transaction commits. Returns the job id so the caller can store it on the version.
     */
    UUID start(GenerationRequest request);

    Optional<JobDto> find(UUID jobId);

    /** Persisted stage events with sequence greater than {@code afterSequence}, oldest first. */
    List<JobStageEvent> events(UUID jobId, int afterSequence);

    /** Path of the SSE stream for a job, relative to the API origin. */
    static String eventsPath(UUID jobId) {
        return "/api/jobs/" + jobId + "/events";
    }
}
