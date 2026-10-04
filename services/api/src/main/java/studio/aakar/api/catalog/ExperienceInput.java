package studio.aakar.api.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import studio.aakar.api.catalog.ExperienceDto.Collection;
import studio.aakar.api.catalog.ExperienceDto.SeasonWindow;

/**
 * {@code AdminExperienceInput} in the management contract: the {@code experience.v1.json} experience row. The catalog
 * service checks what a schema cannot (422): the environment is a known backdrop ({@code validation_failed} naming them),
 * every avatar a family ({@code unknown_family}), every item a Shop item, no avatar, item, motif or collection twice, and
 * each season window's ends are both dates in order or both month-days.
 */
public record ExperienceInput(
        @NotBlank(message = "id is required")
        @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "id must be snake_case starting with a letter")
        @Size(max = 40, message = "id must be at most 40 characters") String id,
        @NotBlank(message = "codename is required") @Size(max = 40, message = "codename must be at most 40 characters") String codename,
        @NotBlank(message = "slug is required")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "slug must be lowercase letters and digits, words joined by single dashes")
        @Size(min = 2, max = 60, message = "slug must be 2-60 characters") String slug,
        @NotBlank(message = "title is required") @Size(max = 80, message = "title must be at most 80 characters") String title,
        @Size(max = 120, message = "tagline must be at most 120 characters") String tagline,
        @Size(max = 500, message = "description must be at most 500 characters") String description,
        @NotBlank(message = "environment is required") @Size(max = 40, message = "environment must be at most 40 characters") String environment,
        @NotNull(message = "surface is required") @Valid ExperienceDto.Surface surface,
        @Pattern(regexp = "^(none|jaipur_heritage|modern_zen|cyber_desi|warli_line|comic_pop)$",
                message = "style must be one of none, jaipur_heritage, modern_zen, cyber_desi, warli_line, comic_pop") String style,
        @Size(max = 24, message = "motif_pack holds at most 24 motifs")
        List<@NotNull(message = "motif_pack entries are required")
                @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "motif_pack entries must be motif ids")
                @Size(max = 40, message = "motif_pack entries must be at most 40 characters") String> motifPack,
        @NotNull(message = "avatars is required (it may be empty)")
        @Size(max = 24, message = "avatars holds at most 24 families")
        List<@NotBlank(message = "avatars entries must be family ids")
                @Size(max = 40, message = "avatars entries must be at most 40 characters") String> avatars,
        @Size(max = 24, message = "items holds at most 24 Shop items")
        List<@NotBlank(message = "items entries must be Shop item slugs")
                @Size(max = 80, message = "items entries must be at most 80 characters") String> items,
        @Size(max = 24, message = "collections holds at most 24 collections") List<@NotNull @Valid Collection> collections,
        @Size(max = 12, message = "season holds at most 12 windows") List<@NotNull @Valid SeasonWindow> season,
        @NotNull(message = "available is required") Boolean available,
        Integer sortOrder) {

    public static final String DEFAULT_STYLE = "none";
    public static final int DEFAULT_SORT_ORDER = 100;

    public String styleOrDefault() {
        return style == null || style.isBlank() ? DEFAULT_STYLE : style;
    }

    public List<String> motifPackOrEmpty() {
        return motifPack == null ? List.of() : motifPack;
    }

    public List<String> itemsOrEmpty() {
        return items == null ? List.of() : items;
    }

    public List<Collection> collectionsOrEmpty() {
        return collections == null ? List.of() : collections;
    }

    public List<SeasonWindow> seasonOrEmpty() {
        return season == null ? List.of() : season;
    }

    public int sortOrderOrDefault() {
        return sortOrder == null ? DEFAULT_SORT_ORDER : sortOrder;
    }
}
