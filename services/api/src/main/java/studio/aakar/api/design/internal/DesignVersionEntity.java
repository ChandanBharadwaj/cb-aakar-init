package studio.aakar.api.design.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import studio.aakar.api.design.VersionStatus;
import studio.aakar.api.studio.DesignCompletedPayload;

/** Immutable once ready: only the generation outcome is written after creation. */
@Entity
@Table(name = "design_versions")
class DesignVersionEntity {

    static final String CREATED_BY_USER = "user";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "design_id", nullable = false)
    private UUID designId;
    @Column(name = "version_no", nullable = false)
    private int versionNo;
    @Column(name = "parent_version_id")
    private UUID parentVersionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> spec;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> template;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> assets;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> geometry;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> printability;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "print_estimate", columnDefinition = "jsonb")
    private Map<String, Object> printEstimate;
    @Column(name = "karigar_note")
    private String karigarNote;
    @Column(name = "job_id")
    private UUID jobId;
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DesignVersionEntity() {
    }

    DesignVersionEntity(UUID designId, int versionNo, UUID parentVersionId, Map<String, Object> spec,
            Map<String, Object> template, String createdBy, Instant now) {
        this.designId = designId;
        this.versionNo = versionNo;
        this.parentVersionId = parentVersionId;
        this.status = VersionStatus.generating;
        this.spec = spec;
        this.template = template;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    UUID id() {
        return id;
    }

    UUID designId() {
        return designId;
    }

    int versionNo() {
        return versionNo;
    }

    UUID parentVersionId() {
        return parentVersionId;
    }

    VersionStatus status() {
        return status;
    }

    Map<String, Object> spec() {
        return spec;
    }

    Map<String, Object> template() {
        return template;
    }

    Map<String, Object> assets() {
        return assets;
    }

    Map<String, Object> geometry() {
        return geometry;
    }

    Map<String, Object> printability() {
        return printability;
    }

    Map<String, Object> printEstimate() {
        return printEstimate;
    }

    String karigarNote() {
        return karigarNote;
    }

    UUID jobId() {
        return jobId;
    }

    String createdBy() {
        return createdBy;
    }

    Instant createdAt() {
        return createdAt;
    }

    void attachJob(UUID jobId) {
        this.jobId = jobId;
    }

    void markReady(DesignCompletedPayload result) {
        this.status = VersionStatus.ready;
        if (result.template() != null) {
            this.template = result.template();
        }
        if (result.spec() != null) {
            this.spec = result.spec();
        }
        this.assets = result.assets();
        this.geometry = result.geometry();
        this.printability = result.printability();
        this.printEstimate = result.printEstimate();
        this.karigarNote = result.karigarNote();
    }

    void markFailed() {
        this.status = VersionStatus.failed;
    }
}
