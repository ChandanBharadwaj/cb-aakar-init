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
import studio.aakar.api.studio.JobDto;
import studio.aakar.api.studio.JobStage;
import studio.aakar.api.studio.JobStatus;

@Entity
@Table(name = "generation_jobs")
class GenerationJobEntity {

    static final String TYPE_GENERATE = "generate";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "design_id", nullable = false)
    private UUID designId;
    @Column(name = "version_id")
    private UUID versionId;
    @Column(name = "version_no", nullable = false)
    private int versionNo;
    @Column(nullable = false)
    private String type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStage stage;
    private String message;
    @Column(name = "error_code")
    private String errorCode;
    private int attempts;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;

    protected GenerationJobEntity() {
    }

    static GenerationJobEntity queued(UUID designId, UUID versionId, int versionNo, Instant now) {
        GenerationJobEntity job = new GenerationJobEntity();
        job.designId = designId;
        job.versionId = versionId;
        job.versionNo = versionNo;
        job.type = TYPE_GENERATE;
        job.status = JobStatus.queued;
        job.stage = JobStage.queued;
        job.message = JobStage.queued.label();
        job.attempts = 0;
        job.createdAt = now;
        return job;
    }

    UUID id() {
        return id;
    }

    UUID designId() {
        return designId;
    }

    UUID versionId() {
        return versionId;
    }

    int versionNo() {
        return versionNo;
    }

    JobStatus status() {
        return status;
    }

    boolean terminal() {
        return status.terminal();
    }

    void markRunning(Instant now) {
        if (status == JobStatus.queued) {
            status = JobStatus.running;
            startedAt = now;
            attempts++;
        }
    }

    void progress(JobStage newStage, String newMessage) {
        stage = newStage;
        message = newMessage;
    }

    void succeed(Instant now) {
        status = JobStatus.succeeded;
        stage = JobStage.ready;
        message = JobStage.ready.label();
        errorCode = null;
        finishedAt = now;
    }

    void fail(String code, String customerMessage, Instant now) {
        status = JobStatus.failed;
        stage = JobStage.failed;
        message = customerMessage;
        errorCode = code;
        finishedAt = now;
    }

    JobDto toDto() {
        return new JobDto(id, designId, versionId, versionNo, type, status, stage, message, errorCode, attempts,
                createdAt, startedAt, finishedAt);
    }
}
