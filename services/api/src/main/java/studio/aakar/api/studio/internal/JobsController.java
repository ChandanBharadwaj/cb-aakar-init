package studio.aakar.api.studio.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.SseHub;
import studio.aakar.api.studio.GenerationJobs;
import studio.aakar.api.studio.JobDto;
import studio.aakar.api.studio.JobStageEvent;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "jobs")
class JobsController {

    private final GenerationJobs jobs;
    private final SseHub<JobStageEvent> stream;

    JobsController(GenerationJobs jobs, SseHub<JobStageEvent> stream) {
        this.jobs = jobs;
        this.stream = stream;
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Job status")
    JobDto job(@PathVariable UUID jobId) {
        return jobs.find(jobId).orElseThrow(() -> ApiProblemException.notFound("Job", jobId));
    }

    @GetMapping(path = "/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-Sent Events stream of job stages",
            description = "Replays persisted `stage` events after `Last-Event-ID` (the sequence), then streams live ones. "
                    + "A `: keep-alive` comment is sent every 15 s. The stream closes after `ready` or `failed`.")
    SseEmitter events(@PathVariable UUID jobId, @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        JobDto job = jobs.find(jobId).orElseThrow(() -> ApiProblemException.notFound("Job", jobId));
        int lastSeen = parseSequence(lastEventId);

        SseHub<JobStageEvent>.Subscription subscription = stream.subscribe(jobId, lastSeen);
        List<JobStageEvent> history = jobs.events(jobId, lastSeen);
        subscription.replayThenGoLive(history);
        if (job.status().terminal()) {
            // Everything this job will ever emit is already persisted; nothing to wait for.
            subscription.close();
        }
        return subscription.emitter();
    }

    private static int parseSequence(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(lastEventId.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
