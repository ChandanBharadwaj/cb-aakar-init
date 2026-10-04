package studio.aakar.api.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import studio.aakar.api.catalog.HardwareItemDto;
import studio.aakar.api.catalog.HardwareItemInput;

@Entity
@Table(name = "hardware_items")
class HardwareItemEntity {

    @Id
    private String sku;
    private String name;
    @Column(name = "unit_cost_paise")
    private long unitCostPaise;
    @Column(name = "weight_g")
    private Double weightG;
    private String supplier;
    private String url;
    private String notes;
    private boolean available;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected HardwareItemEntity() {
    }

    HardwareItemEntity(HardwareItemInput input, Instant now) {
        this.sku = input.sku();
        this.createdAt = now;
        apply(input, now);
    }

    String sku() {
        return sku;
    }

    String name() {
        return name;
    }

    void apply(HardwareItemInput input, Instant now) {
        this.name = input.name().trim();
        this.unitCostPaise = input.unitCostPaise();
        this.weightG = input.weightG();
        this.supplier = blankToNull(input.supplier());
        this.url = blankToNull(input.url());
        this.notes = blankToNull(input.notes());
        this.available = input.availableOrDefault();
        this.updatedAt = now;
    }

    HardwareItemDto toDto() {
        return new HardwareItemDto(sku, name, unitCostPaise, weightG, supplier, url, notes, available, updatedAt);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
