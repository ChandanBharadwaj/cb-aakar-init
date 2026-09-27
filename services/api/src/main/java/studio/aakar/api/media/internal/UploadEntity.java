package studio.aakar.api.media.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;
import studio.aakar.api.media.UploadDto;
import studio.aakar.api.media.UploadKind;
import studio.aakar.api.media.UploadStatus;
import studio.aakar.api.shared.Identity;

/**
 * One customer upload ({@code uploads}, V9). The id is assigned before the file is stored because the storage key
 * ({@code uploads/<owner>/<id>.<format>}) carries it; {@link Persistable} lets the first save insert without a lookup.
 */
@Entity
@Table(name = "uploads")
class UploadEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    @Column(name = "owner_id")
    private UUID ownerId;
    @Column(name = "guest_id")
    private UUID guestId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadKind kind;
    @Column(nullable = false)
    private String format;
    @Column(name = "storage_key", nullable = false)
    private String storageKey;
    private String url;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(nullable = false)
    private long bytes;
    @Column(nullable = false)
    private String sha256;
    @Column(nullable = false)
    private String origin;
    private String provider;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Transient
    private boolean fresh;

    protected UploadEntity() {
    }

    UploadEntity(UUID id, Identity owner, UploadKind kind, String format, String storageKey, String url, String contentType, long bytes,
            String sha256, UploadStatus status, Instant now) {
        this.id = id;
        this.ownerId = owner.isUser() ? owner.id() : null;
        this.guestId = owner.isGuest() ? owner.id() : null;
        this.kind = kind;
        this.format = format;
        this.storageKey = storageKey;
        this.url = url;
        this.contentType = contentType;
        this.bytes = bytes;
        this.sha256 = sha256;
        this.origin = UploadDto.ORIGIN_UPLOAD;
        this.status = status;
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

    UUID ownerId() {
        return ownerId;
    }

    UUID guestId() {
        return guestId;
    }

    UploadKind kind() {
        return kind;
    }

    String format() {
        return format;
    }

    String storageKey() {
        return storageKey;
    }

    String url() {
        return url;
    }

    long bytes() {
        return bytes;
    }

    String sha256() {
        return sha256;
    }

    String origin() {
        return origin;
    }

    String provider() {
        return provider;
    }

    UploadStatus status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }

    boolean ownedBy(Identity identity) {
        if (identity == null || identity.isAnonymous()) {
            return false;
        }
        return identity.isUser() ? identity.id().equals(ownerId) : identity.id().equals(guestId);
    }

    void markStatus(UploadStatus status) {
        this.status = status;
    }
}
