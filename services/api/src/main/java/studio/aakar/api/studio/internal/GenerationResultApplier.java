package studio.aakar.api.studio.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.studio.DesignCompletedPayload;
import studio.aakar.api.studio.DesignFailedPayload;
import studio.aakar.api.studio.DesignProgressPayload;
import studio.aakar.api.studio.Envelope;
import studio.aakar.api.studio.GenerationCompleted;
import studio.aakar.api.studio.GenerationFailed;
import studio.aakar.api.studio.JobStage;
import studio.aakar.api.studio.JobStageEvent;

/**
 * Applies geometry results to a job, whichever transport they came over. Every method locks the job
 * row, so concurrent deliveries serialise; terminal jobs ignore further messages and envelopes are
 * de-duplicated on {@code event_id}, which makes delivery at-least-once safe. Stage events are
 * persisted with a per-job increasing {@code sequence} and pushed to SSE subscribers after commit.
 */
@Component
public class GenerationResultApplier {

    private static final Logger log = LoggerFactory.getLogger(GenerationResultApplier.class);

    private final GenerationJobRepository jobs;
    private final JobEventRepository events;
    private final EnvelopeMapper mapper;
    private final ApplicationEventPublisher publisher;
    private final JobEventStream stream;

    GenerationResultApplier(GenerationJobRepository jobs, JobEventRepository events, EnvelopeMapper mapper,
            ApplicationEventPublisher publisher, JobEventStream stream) {
        this.jobs = jobs;
        this.events = events;
        this.mapper = mapper;
        this.publisher = publisher;
        this.stream = stream;
    }

    /** Routes an envelope by its {@code type}. Unknown jobs raise 404; {@code design.generate} is not a result. */
    @Transactional
    public void apply(Envelope envelope) {
        switch (envelope.type() == null ? "" : envelope.type()) {
            case Envelope.DESIGN_PROGRESS -> applyProgress(envelope.jobId(), envelope);
            case Envelope.DESIGN_COMPLETED -> applyCompleted(envelope.jobId(), mapper.completed(envelope), envelope.eventId());
            case Envelope.DESIGN_FAILED -> applyFailed(envelope.jobId(), mapper.failed(envelope), envelope.eventId());
            default -> throw ApiProblemException.validation("Unsupported envelope type '" + envelope.type()
                    + "'; expected design.progress, design.completed or design.failed");
        }
    }

    @Transactional
    public void markRunning(UUID jobId) {
        GenerationJobEntity job = lock(jobId);
        if (!job.terminal()) {
            job.markRunning(Instant.now());
        }
    }

    @Transactional
    public void applyProgress(UUID jobId, Envelope envelope) {
        GenerationJobEntity job = lock(jobId);
        if (job.terminal() || isDuplicate(jobId, envelope.eventId())) {
            return;
        }
        DesignProgressPayload progress = mapper.progress(envelope);
        JobStage stage = JobStage.parse(progress.stage())
                .orElseThrow(() -> ApiProblemException.validation("Unknown progress stage '" + progress.stage() + "'"));
        if (stage.terminal()) {
            // ready/failed only ever arrive as design.completed / design.failed; a bare progress event cannot end a job.
            log.warn("Ignoring progress event with terminal stage {} for job {}", stage, jobId);
            return;
        }
        Instant now = Instant.now();
        job.markRunning(now);
        String message = progress.message() == null || progress.message().isBlank() ? stage.label() : progress.message();
        job.progress(stage, message);
        Instant at = envelope.occurredAt() == null ? now : envelope.occurredAt();
        publishAfterCommit(List.of(append(job, stage, message, progress.percent(), null, null, at, envelope.eventId())));
    }

    @Transactional
    public void applyCompleted(UUID jobId, DesignCompletedPayload payload, UUID eventId) {
        GenerationJobEntity job = lock(jobId);
        if (job.terminal() || isDuplicate(jobId, eventId)) {
            log.info("Ignoring duplicate completion for job {} (status {})", jobId, job.status());
            return;
        }
        Instant now = Instant.now();
        job.markRunning(now);
        UUID versionId = job.versionId();

        // The design module marks the version ready inside this transaction, before any client hears "ready".
        publisher.publishEvent(new GenerationCompleted(jobId, job.designId(), versionId, payload));

        List<JobStageEvent> emitted = new ArrayList<>();
        emitted.add(append(job, JobStage.pricing, JobStage.pricing.label(), 95, versionId, null, now, eventId));
        emitted.add(append(job, JobStage.ready, JobStage.ready.label(), 100, versionId, null, now, null));
        job.succeed(now);
        publishAfterCommit(emitted);
        log.info("Job {} succeeded: version {} ready", jobId, versionId);
    }

    @Transactional
    public void applyFailed(UUID jobId, DesignFailedPayload payload, UUID eventId) {
        GenerationJobEntity job = lock(jobId);
        if (job.terminal() || isDuplicate(jobId, eventId)) {
            log.info("Ignoring duplicate failure for job {} (status {})", jobId, job.status());
            return;
        }
        Instant now = Instant.now();
        job.markRunning(now);
        String code = payload.code() == null || payload.code().isBlank() ? DesignFailedPayload.BUILD_ERROR : payload.code();
        String message = payload.message() == null || payload.message().isBlank()
                ? "We could not build this design. Please try different values." : payload.message();
        UUID versionId = job.versionId();

        publisher.publishEvent(new GenerationFailed(jobId, job.designId(), versionId, code, message));

        JobStageEvent event = append(job, JobStage.failed, message, null, versionId, code, now, eventId);
        job.fail(code, message, now);
        publishAfterCommit(List.of(event));
        log.warn("Job {} failed with {}: {}", jobId, code, message);
    }

    private GenerationJobEntity lock(UUID jobId) {
        return jobs.lockById(jobId).orElseThrow(() -> ApiProblemException.notFound("Job", jobId));
    }

    private boolean isDuplicate(UUID jobId, UUID eventId) {
        return eventId != null && events.existsByJobIdAndSourceEventId(jobId, eventId);
    }

    private JobStageEvent append(GenerationJobEntity job, JobStage stage, String message, Integer percent, UUID versionId,
            String errorCode, Instant at, UUID sourceEventId) {
        int sequence = events.maxSequence(job.id()) + 1;
        JobEventEntity row = events.saveAndFlush(new JobEventEntity(job.id(), sequence, stage, message, percent, versionId,
                errorCode, at, sourceEventId));
        return row.toEvent();
    }

    private void publishAfterCommit(List<JobStageEvent> emitted) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    emitted.forEach(stream::publish);
                }
            });
        } else {
            emitted.forEach(stream::publish);
        }
    }
}
