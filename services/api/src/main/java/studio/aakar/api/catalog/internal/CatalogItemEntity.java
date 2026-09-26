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

@Entity
@Table(name = "catalog_items")
class CatalogItemEntity {

    @Id
    private String slug;
    private String name;
    private String category;
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

    CatalogItemDto toDto() {
        return new CatalogItemDto(slug, name, category, description, templateId,
                defaultParams == null ? Map.of() : defaultParams, defaultMaterial, basePricePaise, specsLine,
                environment, available, media == null ? List.of() : media);
    }
}
