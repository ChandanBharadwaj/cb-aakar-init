package studio.aakar.api.media.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;
import studio.aakar.api.media.UploadReview;

/** A flagged upload waiting for (or decided by) a reviewer ({@code content_reviews}, V9). */
@Entity
@Table(name = "content_reviews")
class ContentReviewEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    @Column(name = "upload_id", nullable = false)
    private UUID uploadId;
    @Column(name = "design_id")
    private UUID designId;
    @Column(nullable = false)
    private String reason;
    @Column(nullable = false)
    private String status;
    @Column(name = "decision_note")
    private String decisionNote;
    @Column(name = "reviewer_email")
    private String reviewerEmail;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Transient
    private boolean fresh;

    protected ContentReviewEntity() {
    }

    ContentReviewEntity(UUID id, UUID uploadId, String reason, Instant now) {
        this.id = id;
        this.uploadId = uploadId;
        this.reason = reason;
        this.status = UploadReview.PENDING;
        this.createdAt = now;
        this.fresh = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void loaded() {
        this.fresh = false;
    }

    UUID id() {
        return id;
    }

    UUID uploadId() {
        return uploadId;
    }

    String status() {
        return status;
    }

    boolean pending() {
        return UploadReview.PENDING.equals(status);
    }

    String decisionNote() {
        return decisionNote;
    }

    void decide(boolean approve, String note, String reviewerEmail, Instant now) {
        this.status = approve ? UploadReview.APPROVED : UploadReview.REJECTED;
        this.decisionNote = note;
        this.reviewerEmail = reviewerEmail;
        this.decidedAt = now;
    }

    UploadReview toDto() {
        return new UploadReview(id, uploadId, reason, status, decisionNote, reviewerEmail, createdAt, decidedAt);
    }
}
