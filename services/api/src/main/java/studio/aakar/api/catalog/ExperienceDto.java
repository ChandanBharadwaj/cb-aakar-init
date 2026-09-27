package studio.aakar.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * An experience (Duniya) on the Shop: {@code Experience} in the storefront contract. The row fields are those of
 * {@code experience.v1.json}; {@code avatars} are expanded to the families that can be ordered today (available and backed
 * by a live template, the {@code GET /api/families} view) in the experience's order, and {@code items} to its curated Shop
 * items in order. {@code priceFromPaise} is the lowest floor among those avatars ("from ₹249"), or null. The management
 * view with ids instead of expansions is {@link AdminExperienceDto}. Brand copy ({@code codename}, {@code title},
 * {@code tagline}) is data; the ids never change. Nulls are left out.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExperienceDto(
        String id,
        String codename,
        String slug,
        String title,
        String tagline,
        String description,
        String environment,
        Surface surface,
        String style,
        List<String> motifPack,
        List<FamilyDto> avatars,
        List<CatalogItemDto> items,
        List<Collection> collections,
        List<SeasonWindow> season,
        boolean available,
        int sortOrder,
        Long priceFromPaise) {

    /** The {@code design-spec.v1.json} style values an experience may preset; {@code none} presets nothing. */
    public static final List<String> STYLES = List.of("none", "jaipur_heritage", "modern_zen", "cyber_desi", "warli_line", "comic_pop");
    static final String HEX = "^#[0-9A-Fa-f]{6}$";
    static final String DATE_OR_MONTH_DAY = "^([0-9]{4}-[0-9]{2}-[0-9]{2}|--[0-9]{2}-[0-9]{2})$";

    public ExperienceDto {
        motifPack = motifPack == null ? List.of() : List.copyOf(motifPack);
        avatars = avatars == null ? List.of() : List.copyOf(avatars);
        items = items == null ? List.of() : List.copyOf(items);
        collections = collections == null ? List.of() : List.copyOf(collections);
        season = season == null ? List.of() : List.copyOf(season);
    }

    /**
     * {@code surface}: page theming on the cream paper (ADR-0001 keeps it). {@code accent} is a brand colour for chips, links
     * and buttons; {@code paperTint} a light tint over the paper (null keeps plain cream); {@code heroMedia} the URL or site
     * path of the hero image or loop (null until one is shot).
     */
    public record Surface(
            @NotBlank(message = "surface.accent is required")
            @Pattern(regexp = HEX, message = "surface.accent must be a #RRGGBB colour") String accent,
            @Pattern(regexp = HEX, message = "surface.paper_tint must be a #RRGGBB colour or null") String paperTint,
            @Size(max = 500, message = "surface.hero_media must be at most 500 characters") String heroMedia) {
    }

    /** A sub-collection, e.g. a licensed universe; {@code licenceRef} names the agreement that allows it (null for original work). */
    public record Collection(
            @NotBlank(message = "collections.id is required")
            @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "collections.id must be snake_case starting with a letter")
            @Size(max = 40, message = "collections.id must be at most 40 characters") String id,
            @NotBlank(message = "collections.title is required")
            @Size(max = 80, message = "collections.title must be at most 80 characters") String title,
            @Size(max = 120, message = "collections.licence_ref must be at most 120 characters") String licenceRef) {
    }

    /**
     * A {@code season} window: both ends calendar dates ({@code 2026-10-20}, {@code startsOn ≤ endsOn}) or both ISO month-days
     * ({@code --10-01}) that recur every year and may wrap the new year. The catalog service checks the pairing and the dates.
     */
    public record SeasonWindow(
            @NotBlank(message = "season.starts_on is required")
            @Pattern(regexp = DATE_OR_MONTH_DAY, message = "season.starts_on must be a date (2026-10-20) or a month-day (--10-01)") String startsOn,
            @NotBlank(message = "season.ends_on is required")
            @Pattern(regexp = DATE_OR_MONTH_DAY, message = "season.ends_on must be a date (2026-11-10) or a month-day (--11-30)") String endsOn,
            @NotBlank(message = "season.label is required") @Size(max = 40, message = "season.label must be at most 40 characters") String label) {
    }
}
