package studio.aakar.api.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * An experience (Duniya) as stored: {@code AdminExperience} in the management contract, i.e. the {@code experience.v1.json}
 * row plus {@code updatedAt}. {@code avatars} are family ids and {@code items} Shop item slugs, in display order, whether or
 * not they can be ordered today, so the portal round-trips what it edits ({@link ExperienceInput}); the audit log keeps it as
 * before/after. The storefront view with families and items expanded is {@link ExperienceDto}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminExperienceDto(
        String id,
        String codename,
        String slug,
        String title,
        String tagline,
        String description,
        String environment,
        ExperienceDto.Surface surface,
        String style,
        List<String> motifPack,
        List<String> avatars,
        List<String> items,
        List<ExperienceDto.Collection> collections,
        List<ExperienceDto.SeasonWindow> season,
        boolean available,
        int sortOrder,
        Instant updatedAt) {

    public AdminExperienceDto {
        motifPack = motifPack == null ? List.of() : List.copyOf(motifPack);
        avatars = avatars == null ? List.of() : List.copyOf(avatars);
        items = items == null ? List.of() : List.copyOf(items);
        collections = collections == null ? List.of() : List.copyOf(collections);
        season = season == null ? List.of() : List.copyOf(season);
    }
}
