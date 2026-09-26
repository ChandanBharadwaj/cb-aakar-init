package studio.aakar.api.studio.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import studio.aakar.api.shared.AakarProperties;
import studio.aakar.api.studio.DesignGeneratePayload;
import studio.aakar.api.studio.Envelope;
import studio.aakar.api.studio.GenerationJobs;
import studio.aakar.api.studio.GenerationRequest;
import studio.aakar.api.studio.JobDto;
import studio.aakar.api.studio.JobStage;
import studio.aakar.api.studio.JobStageEvent;

@Service
class GenerationJobService implements GenerationJobs {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    private final GenerationJobRepository jobs;
    private final JobEventRepository events;
    private final OutboxEventRepository outbox;
    private final EnvelopeMapper mapper;
    private final JobDispatcher dispatcher;
    private final ApplicationEventPublisher publisher;
    private final AakarProperties properties;

    GenerationJobService(GenerationJobRepository jobs, JobEventRepository events, OutboxEventRepository outbox,
            EnvelopeMapper mapper, JobDispatcher dispatcher, ApplicationEventPublisher publisher, AakarProperties properties) {
        this.jobs = jobs;
        this.events = events;
        this.outbox = outbox;
        this.mapper = mapper;
        this.dispatcher = dispatcher;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Override
    @Transactional
    public UUID start(GenerationRequest request) {
        Instant now = Instant.now();
        GenerationJobEntity job = jobs.save(GenerationJobEntity.queued(request.designId(), request.versionId(), request.versionNo(), now));
        events.save(new JobEventEntity(job.id(), 1, JobStage.queued, JobStage.queued.label(), 0, null, null, now, null));

        DesignGeneratePayload payload = mapper.generatePayload(job.id(), request, callbackUrl(job.id()));
        Envelope envelope = mapper.generateEnvelope(payload);
        OutboxEventEntity row = outbox.save(new OutboxEventEntity("generation_job", job.id().toString(),
                Envelope.DESIGN_GENERATE, mapper.toMap(envelope), now));

        publisher.publishEvent(new JobQueued(job.id(), row.id(), payload));
        log.info("Job {} queued for design {} v{}", job.id(), request.designId(), request.versionNo());
        return job.id();
    }

    /** Runs after the job row is committed, so the geometry service (or its callback) can always find it. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchAfterCommit(JobQueued queued) {
        dispatcher.dispatch(queued.jobId(), queued.payload());
        outbox.markPublished(queued.outboxId(), Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobDto> find(UUID jobId) {
        return jobs.findById(jobId).map(GenerationJobEntity::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobStageEvent> events(UUID jobId, int afterSequence) {
        return events.findByJobIdAndSequenceGreaterThanOrderBySequenceAsc(jobId, afterSequence).stream()
                .map(JobEventEntity::toEvent)
                .toList();
    }

    private String callbackUrl(UUID jobId) {
        return properties.api().publicUrl() + "/internal/jobs/" + jobId + "/callback";
    }
}
