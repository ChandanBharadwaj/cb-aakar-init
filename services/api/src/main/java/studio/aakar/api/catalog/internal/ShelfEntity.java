package studio.aakar.api.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import studio.aakar.api.catalog.ShelfDto;

/** A Shop shelf (catalog category); {@code catalog_items.category} and {@code template_families.shelf} reference it. */
@Entity
@Table(name = "shelves")
class ShelfEntity {

    @Id
    private String id;
    private String label;
    @Column(name = "sort_order")
    private int sortOrder;

    protected ShelfEntity() {
    }

    String id() {
        return id;
    }

    ShelfDto toDto() {
        return new ShelfDto(id, label, sortOrder);
    }
}
