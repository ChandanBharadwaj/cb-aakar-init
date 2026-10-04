package studio.aakar.api.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import studio.aakar.api.catalog.EnvironmentDto;

/**
 * A viewer backdrop (Mahaul): read-only reference data seeded from {@code experiences.json}. Experiences, families and Shop
 * items reference it by id ({@code environment}); {@code preset_key} names the storefront preset that renders it.
 */
@Entity
@Table(name = "environments")
class EnvironmentEntity {

    @Id
    private String id;
    private String label;
    private String surface;
    @Column(name = "preset_key")
    private String presetKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> palette;
    @Column(name = "sort_order")
    private int sortOrder;

    protected EnvironmentEntity() {
    }

    String id() {
        return id;
    }

    EnvironmentDto toDto() {
        return new EnvironmentDto(id, label, surface, presetKey, palette, sortOrder);
    }
}
