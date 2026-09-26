package studio.aakar.api.design;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * {@code CreateDesignRequest}: Shop path ({@code catalog_item_slug}), Remix-lite / direct path
 * ({@code template_id} + optional {@code params}, {@code material}), Create path ({@code prompt}, Phase 2).
 */
public record CreateDesignRequest(
        @NotNull(message = "source is required (shop, create or remix)") DesignSource source,
        String catalogItemSlug,
        String templateId,
        Map<String, Object> params,
        String material,
        @Size(max = 500, message = "prompt must be at most 500 characters") String prompt,
        @Size(max = 80, message = "title must be at most 80 characters") String title) {

    public boolean hasPrompt() {
        return prompt != null && !prompt.isBlank();
    }
}
