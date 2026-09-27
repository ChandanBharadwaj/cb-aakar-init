package studio.aakar.api.catalog.internal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import studio.aakar.api.catalog.ExperienceInput;

/**
 * One experience (Duniya): the {@code experience.v1.json} row. {@code surface}, {@code motif_pack}, {@code collections} and
 * {@code season} are JSONB documents held verbatim (snake_case keys, {@link ExperienceJson} converts them); the ordered
 * avatars and curated items live in {@code experience_avatars} / {@code experience_items}. Every write replaces both lists,
 * which Hibernate turns into a delete and a re-insert of the rows, so a new order never collides with the old one.
 */
@Entity
@Table(name = "experiences")
class ExperienceEntity {

    @Id
    private String id;
    private String codename;
    private String slug;
    private String title;
    private String tagline;
    private String description;
    private String environment;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> surface;
    private String style;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "motif_pack", columnDefinition = "jsonb")
    private List<String> motifPack;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> collections;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> season;
    private boolean available;
    @Column(name = "sort_order")
    private int sortOrder;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "experience_avatars", joinColumns = @JoinColumn(name = "experience_id"))
    @OrderBy("sortOrder")
    private List<ExperienceAvatar> avatars = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "experience_items", joinColumns = @JoinColumn(name = "experience_id"))
    @OrderBy("sortOrder")
    private List<ExperienceItem> items = new ArrayList<>();

    protected ExperienceEntity() {
    }

    ExperienceEntity(String id, ExperienceInput input, ExperienceJson json, Instant now) {
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

    String slug() {
        return slug;
    }

    String title() {
        return title;
    }

    String tagline() {
        return tagline;
    }

    String description() {
        return description;
    }

    String environment() {
        return environment;
    }

    Map<String, Object> surface() {
        return surface == null ? Map.of() : surface;
    }

    String style() {
        return style;
    }

    List<String> motifPack() {
        return motifPack == null ? List.of() : motifPack;
    }

    List<Map<String, Object>> collections() {
        return collections == null ? List.of() : collections;
    }

    List<Map<String, Object>> season() {
        return season == null ? List.of() : season;
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

    /** Family ids in display order. */
    List<String> avatarIds() {
        return avatars.stream().map(ExperienceAvatar::familyId).toList();
    }

    /** Shop item slugs in display order. */
    List<String> itemSlugs() {
        return items.stream().map(ExperienceItem::catalogItemSlug).toList();
    }

    void apply(ExperienceInput input, ExperienceJson json, Instant now) {
        this.codename = input.codename().trim();
        this.slug = input.slug();
        this.title = input.title().trim();
        this.tagline = blankToNull(input.tagline());
        this.description = blankToNull(input.description());
        this.environment = input.environment().trim();
        this.surface = json.toMap(input.surface());
        this.style = input.styleOrDefault();
        this.motifPack = List.copyOf(input.motifPackOrEmpty());
        this.collections = json.toMaps(input.collectionsOrEmpty());
        this.season = json.toMaps(input.seasonOrEmpty());
        this.available = input.available();
        this.sortOrder = input.sortOrderOrDefault();
        // new list instances: Hibernate deletes the old rows and inserts these in order
        List<ExperienceAvatar> orderedAvatars = new ArrayList<>();
        List<String> familyIds = input.avatars();
        for (int i = 0; i < familyIds.size(); i++) {
            orderedAvatars.add(new ExperienceAvatar(familyIds.get(i).trim(), i + 1));
        }
        this.avatars = orderedAvatars;
        List<ExperienceItem> orderedItems = new ArrayList<>();
        List<String> slugs = input.itemsOrEmpty();
        for (int i = 0; i < slugs.size(); i++) {
            orderedItems.add(new ExperienceItem(slugs.get(i).trim(), i + 1));
        }
        this.items = orderedItems;
        this.updatedAt = now;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
