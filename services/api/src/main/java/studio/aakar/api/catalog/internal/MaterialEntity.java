package studio.aakar.api.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import studio.aakar.api.catalog.MaterialDto;

@Entity
@Table(name = "materials")
class MaterialEntity {

    @Id
    private String id;
    private String name;
    private String filament;
    @Column(name = "density_g_cm3")
    private double densityGCm3;
    @Column(name = "finish_class")
    private String finishClass;
    @Column(name = "rate_per_g_paise")
    private long ratePerGPaise;
    @Column(name = "heat_safe")
    private boolean heatSafe;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> pbr;
    @Column(name = "sort_order")
    private int sortOrder;

    protected MaterialEntity() {
    }

    MaterialDto toDto() {
        return new MaterialDto(id, name, filament, densityGCm3, finishClass, ratePerGPaise, heatSafe,
                pbr == null ? Map.of() : pbr);
    }
}
