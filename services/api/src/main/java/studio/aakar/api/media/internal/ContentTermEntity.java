package studio.aakar.api.media.internal;

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
import studio.aakar.api.media.ContentTermDto;
import studio.aakar.api.media.ContentTermKind;

/** A name the studio won't print ({@code content_terms}, V13); never renamed or deleted, switched off instead. */
@Entity
@Table(name = "content_terms")
class ContentTermEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String term;
    @Column(name = "normalised_term", nullable = false)
    private String normalisedTerm;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentTermKind kind;
    private String reason;
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContentTermEntity() {
    }

    ContentTermEntity(String term, String normalisedTerm, ContentTermKind kind, String reason, boolean active, Instant now) {
        this.term = term;
        this.normalisedTerm = normalisedTerm;
        this.createdAt = now;
        apply(kind, reason, active, now);
    }

    UUID id() {
        return id;
    }

    String term() {
        return term;
    }

    String normalisedTerm() {
        return normalisedTerm;
    }

    void apply(ContentTermKind kind, String reason, boolean active, Instant now) {
        this.kind = kind;
        this.reason = reason == null || reason.isBlank() ? null : reason.trim();
        this.active = active;
        this.updatedAt = now;
    }

    ContentTermDto toDto() {
        return new ContentTermDto(id, term, kind, reason, active, normalisedTerm, TermMatcher.wholeWord(normalisedTerm), createdAt, updatedAt);
    }
}
