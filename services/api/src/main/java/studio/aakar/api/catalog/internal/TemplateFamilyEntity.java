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
import studio.aakar.api.catalog.FamilyInput;

/**
 * One outcome family (Avatar): the {@code template-family.v1.json} row. The envelope, hardware default, material
 * rules and content slot are JSONB documents held verbatim (snake_case keys); {@link FamilyJson} converts them to
 * and from the typed records of {@code FamilyDto}.
 */
@Entity
@Table(name = "template_families")
class TemplateFamilyEntity {

    @Id
    private String id;
    private String codename;
    private String name;
    private String tagline;
    private String description;
    private String kind;
    private String tier;
    private String shelf;
    @Column(name = "demand_rank")
    private Integer demandRank;
    @Column(name = "default_template_id")
    private String defaultTemplateId;
    private String environment;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "size_envelope", columnDefinition = "jsonb")
    private Map<String, Object> sizeEnvelope;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> hardware;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "material_rules", columnDefinition = "jsonb")
    private Map<String, Object> materialRules;
    @Column(name = "shape_tolerance")
    private String shapeTolerance;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_slot", columnDefinition = "jsonb")
    private Map<String, Object> contentSlot;
    private boolean available;
    @Column(name = "sort_order")
    private int sortOrder;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected TemplateFamilyEntity() {
    }

    TemplateFamilyEntity(String id, FamilyInput input, FamilyJson json, Instant now) {
        this.id = id;
        this.createdAt = now;
        apply(input, json, now);
    }

    String id() {
        return id;
    }

    String codename() {
        return codename;
    }

    String name() {
        return name;
    }

    String tagline() {
        return tagline;
    }

    String description() {
        return description;
    }

    String kind() {
        return kind;
    }

    String tier() {
        return tier;
    }

    String shelf() {
        return shelf;
    }

    Integer demandRank() {
        return demandRank;
    }

    String defaultTemplateId() {
        return defaultTemplateId;
    }

    String environment() {
        return environment;
    }

    Map<String, Object> sizeEnvelope() {
        return sizeEnvelope;
    }

    List<Map<String, Object>> hardware() {
        return hardware == null ? List.of() : hardware;
    }

    Map<String, Object> materialRules() {
        return materialRules == null ? Map.of() : materialRules;
    }

    String shapeTolerance() {
        return shapeTolerance;
    }

    Map<String, Object> contentSlot() {
        return contentSlot == null ? Map.of() : contentSlot;
    }

    boolean available() {
        return available;
    }

    int sortOrder() {
        return sortOrder;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    void apply(FamilyInput input, FamilyJson json, Instant now) {
        this.codename = input.codename().trim();
        this.name = input.name().trim();
        this.tagline = blankToNull(input.tagline());
        this.description = blankToNull(input.description());
        this.kind = input.kind();
        this.tier = input.tier();
        this.shelf = input.shelf().trim();
        this.demandRank = input.demandRank();
        this.defaultTemplateId = input.defaultTemplateId().trim();
        this.environment = input.environmentOrDefault();
        this.sizeEnvelope = json.toMap(input.sizeEnvelopeMm());
        this.hardware = json.toMaps(input.hardwareOrEmpty());
        this.materialRules = json.toMap(input.materialRulesOrDefault());
        this.shapeTolerance = input.shapeTolerance();
        this.contentSlot = json.toMap(input.contentSlot());
        this.available = input.available();
        this.sortOrder = input.sortOrderOrDefault();
        this.updatedAt = now;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
