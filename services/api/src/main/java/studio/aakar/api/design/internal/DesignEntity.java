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
import java.util.UUID;
import studio.aakar.api.design.DesignSource;

@Entity
@Table(name = "designs")
class DesignEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "owner_id")
    private UUID ownerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DesignSource source;
    @Column(name = "catalog_item_slug")
    private String catalogItemSlug;
    @Column(nullable = false)
    private String title;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DesignEntity() {
    }

    DesignEntity(DesignSource source, String catalogItemSlug, String title, Instant now) {
        this.source = source;
        this.catalogItemSlug = catalogItemSlug;
        this.title = title;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() {
        return id;
    }

    DesignSource source() {
        return source;
    }

    String catalogItemSlug() {
        return catalogItemSlug;
    }

    String title() {
        return title;
    }

    Instant createdAt() {
        return createdAt;
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }
}
