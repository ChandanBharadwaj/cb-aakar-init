package studio.aakar.api.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.catalog.CatalogItemInput;

@Entity
@Table(name = "catalog_items")
class CatalogItemEntity {

    @Id
    private String slug;
    private String name;
    private String category;
    @Column(name = "family_id")
    private String familyId;
    private String description;
    @Column(name = "template_id")
    private String templateId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_params", columnDefinition = "jsonb")
    private Map<String, Object> defaultParams;
    @Column(name = "default_material")
    private String defaultMaterial;
    @Column(name = "base_price_paise")
    private long basePricePaise;
    @Column(name = "specs_line")
    private String specsLine;
    private String environment;
    private boolean available;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> media;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CatalogItemEntity() {
    }

    CatalogItemEntity(CatalogItemInput input, Instant now) {
        this.slug = input.slug();
        this.createdAt = now;
        apply(input, now);
    }

    String slug() {
        return slug;
    }

    String templateId() {
        return templateId;
    }

    void apply(CatalogItemInput input, Instant now) {
        this.name = input.name().trim();
        this.category = input.category().trim();
        this.familyId = input.familyId() == null || input.familyId().isBlank() ? null : input.familyId().trim();
        this.description = input.description() == null || input.description().isBlank() ? null : input.description().trim();
        this.templateId = input.templateId().trim();
        this.defaultParams = input.defaultParams() == null ? Map.of() : input.defaultParams();
        this.defaultMaterial = input.defaultMaterial().trim();
        this.basePricePaise = input.basePricePaise();
        this.specsLine = input.specsLine();
        this.environment = input.environment() == null || input.environment().isBlank() ? null : input.environment().trim();
        this.available = input.available();
        this.media = input.media() == null ? List.of() : input.media();
        this.updatedAt = now;
    }

    CatalogItemDto toDto() {
        return new CatalogItemDto(slug, name, category, familyId, description, templateId,
                defaultParams == null ? Map.of() : defaultParams, defaultMaterial, basePricePaise, specsLine,
                environment, available, media == null ? List.of() : media);
    }
}
