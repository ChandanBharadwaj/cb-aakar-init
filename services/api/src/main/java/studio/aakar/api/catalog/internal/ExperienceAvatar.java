package studio.aakar.api.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One row of {@code experience_avatars}: a family shown in an experience, at its place in the order. */
@Embeddable
class ExperienceAvatar {

    @Column(name = "family_id", nullable = false)
    private String familyId;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ExperienceAvatar() {
    }

    ExperienceAvatar(String familyId, int sortOrder) {
        this.familyId = familyId;
        this.sortOrder = sortOrder;
    }

    String familyId() {
        return familyId;
    }
}
