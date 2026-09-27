package studio.aakar.api.templates.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Staff switch per template ({@code template_flags}); a template without a row is live. */
@Entity
@Table(name = "template_flags")
class TemplateFlagEntity {

    @Id
    @Column(name = "template_id")
    private String templateId;
    @Column(nullable = false)
    private boolean live;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TemplateFlagEntity() {
    }

    TemplateFlagEntity(String templateId, boolean live, Instant now) {
        this.templateId = templateId;
        this.live = live;
        this.updatedAt = now;
    }

    String templateId() {
        return templateId;
    }

    boolean live() {
        return live;
    }

    void set(boolean live, Instant now) {
        this.live = live;
        this.updatedAt = now;
    }
}
