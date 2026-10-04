package studio.aakar.api.catalog.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.EnvironmentDto;
import studio.aakar.api.catalog.ExperienceDto;

/** Experiences (Duniya) for the Shop's second persona, and the viewer backdrops (Mahaul) every {@code environment} names. */
@RestController
@RequestMapping("/api")
@Tag(name = "experiences")
class ExperiencesController {

    private final Catalog catalog;

    ExperiencesController(Catalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/experiences")
    @Operation(summary = "Experiences (Duniya) on the Shop, in display order",
            description = "Only available experiences. `avatars` are the experience's families that can be ordered today (available and backed "
                    + "by a live template), in its order and in the shape of `GET /api/families`; `items` its curated Shop items; "
                    + "`price_from_paise` the lowest floor among the avatars. Needs the geometry service for readiness (503 `geometry_unavailable`).")
    List<ExperienceDto> list() {
        return catalog.experiences(false);
    }

    @GetMapping("/experiences/{slug}")
    @Operation(summary = "Experience by URL slug", description = "Any experience, available or not (`available` says whether the Shop lists "
            + "it), so a deep link can say coming soon; 404 `unknown_experience` otherwise.")
    ExperienceDto bySlug(@PathVariable String slug) {
        return catalog.experience(slug).orElseThrow(() -> CatalogService.unknownExperience(slug));
    }

    @GetMapping("/environments")
    @Operation(summary = "Viewer backdrops (Mahaul) in display order", description = "The reference data behind every `environment` field; "
            + "`preset_key` names the storefront viewer preset that renders the backdrop.")
    List<EnvironmentDto> environments() {
        return catalog.environments();
    }
}
