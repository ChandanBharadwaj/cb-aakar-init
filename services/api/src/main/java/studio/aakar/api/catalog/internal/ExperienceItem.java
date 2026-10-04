package studio.aakar.api.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One row of {@code experience_items}: a Shop item an experience curates, at its place in the order. */
@Embeddable
class ExperienceItem {

    @Column(name = "catalog_item_slug", nullable = false)
    private String catalogItemSlug;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ExperienceItem() {
    }

    ExperienceItem(String catalogItemSlug, int sortOrder) {
        this.catalogItemSlug = catalogItemSlug;
        this.sortOrder = sortOrder;
    }

    String catalogItemSlug() {
        return catalogItemSlug;
    }
}
