package studio.aakar.api.admin.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A staff upload (QC photo) or a generated document, as kept by the media store. */
@Entity
@Table(name = "media_assets")
class MediaAssetEntity {

    static final String KIND_QC_PHOTO = "qc_photo";
    static final String KIND_PACKAGING_CARD = "packaging_card";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private String kind;
    @Column(name = "order_id")
    private UUID orderId;
    @Column(name = "\"key\"", nullable = false)
    private String key;
    @Column(nullable = false)
    private String url;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(nullable = false)
    private long bytes;
    private String note;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MediaAssetEntity() {
    }

    MediaAssetEntity(String kind, UUID orderId, String key, String url, String contentType, long bytes, String note, Instant now) {
        this.kind = kind;
        this.orderId = orderId;
        this.key = key;
        this.url = url;
        this.contentType = contentType;
        this.bytes = bytes;
        this.note = note;
        this.createdAt = now;
    }

    MediaAssetDto toDto() {
        return new MediaAssetDto(id, kind, orderId, url, contentType, bytes, note, createdAt);
    }
}
