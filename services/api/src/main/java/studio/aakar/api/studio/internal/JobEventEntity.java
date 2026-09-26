package studio.aakar.api.studio.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import studio.aakar.api.studio.JobStage;
import studio.aakar.api.studio.JobStageEvent;

/** Append-only stage log per job; {@code sequence} is unique per job and is the SSE event id. */
@Entity
@Table(name = "job_events")
class JobEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(nullable = false)
    private int sequence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStage stage;
    @Column(nullable = false)
    private String message;
    private Integer percent;
    @Column(name = "version_id")
    private UUID versionId;
    @Column(name = "error_code")
    private String errorCode;
    @Column(nullable = false)
    private Instant at;
    /** {@code event_id} of the envelope this came from; null for stages the API emitted itself. */
    @Column(name = "source_event_id")
    private UUID sourceEventId;

    protected JobEventEntity() {
    }

    JobEventEntity(UUID jobId, int sequence, JobStage stage, String message, Integer percent, UUID versionId,
            String errorCode, Instant at, UUID sourceEventId) {
        this.jobId = jobId;
        this.sequence = sequence;
        this.stage = stage;
        this.message = message;
        this.percent = percent;
        this.versionId = versionId;
        this.errorCode = errorCode;
        this.at = at;
        this.sourceEventId = sourceEventId;
    }

    int sequence() {
        return sequence;
    }

    JobStageEvent toEvent() {
        return new JobStageEvent(jobId, sequence, stage, message, percent, versionId, errorCode, at);
    }
}
