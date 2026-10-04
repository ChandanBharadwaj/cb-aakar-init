package studio.aakar.api.design;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * {@code CreateDesignRequest}: Shop path ({@code catalog_item_slug}), Remix-lite / direct path ({@code template_id} +
 * optional {@code params}, {@code material}), Avatar path ({@code family_id} + optional {@code template_id} + the
 * Chhaap {@code features}), Swaroop path ({@code source: upload}, {@code family_id: raw_print}, one {@code hero_mesh}),
 * Create path ({@code prompt} without a family, Phase 2). Features follow {@code design-spec.v1.json#/$defs/feature};
 * a content source names an {@code upload_id} and the API fills the rest. Any path may name the Duniya experience the
 * customer started from ({@code experience_id}), which the design keeps.
 */
public record CreateDesignRequest(
        @NotNull(message = "source is required (shop, create, remix or upload)") DesignSource source,
        String catalogItemSlug,
        String familyId,
        String templateId,
        Map<String, Object> params,
        String material,
        @Size(max = 8, message = "features must hold at most 8 items") List<Map<String, Object>> features,
        @Size(max = 500, message = "prompt must be at most 500 characters") String prompt,
        @Size(max = 80, message = "title must be at most 80 characters") String title,
        @Size(max = 40, message = "experience_id must be at most 40 characters") String experienceId) {

    public boolean hasPrompt() {
        return prompt != null && !prompt.isBlank();
    }

    public boolean hasFamily() {
        return familyId != null && !familyId.isBlank();
    }

    public boolean hasExperience() {
        return experienceId != null && !experienceId.isBlank();
    }
}
