package studio.aakar.api.catalog.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import studio.aakar.api.catalog.AdminExperienceDto;
import studio.aakar.api.catalog.CatalogItemDto;
import studio.aakar.api.catalog.ExperienceDto;
import studio.aakar.api.catalog.FamilyDto;

/**
 * Converts between the JSONB documents on {@link ExperienceEntity} (snake_case maps, exactly as seeded from
 * {@code experiences.json}) and the typed records of {@link ExperienceDto}, using the application's snake_case
 * {@link ObjectMapper} so the stored keys are the contract's.
 */
@Component
class ExperienceJson {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> MAPS = new TypeReference<>() { };

    private final ObjectMapper json;

    ExperienceJson(ObjectMapper json) {
        this.json = json;
    }

    Map<String, Object> toMap(Object value) {
        return value == null ? null : json.convertValue(value, MAP);
    }

    List<Map<String, Object>> toMaps(List<?> values) {
        return values == null ? List.of() : json.convertValue(values, MAPS);
    }

    /** The row as stored, with ids: what the portal edits and the audit log keeps. */
    AdminExperienceDto toAdminDto(ExperienceEntity x) {
        return new AdminExperienceDto(x.id(), x.codename(), x.slug(), x.title(), x.tagline(), x.description(), x.environment(), surface(x),
                x.style(), x.motifPack(), x.avatarIds(), x.itemSlugs(), all(x.collections(), ExperienceDto.Collection.class),
                all(x.season(), ExperienceDto.SeasonWindow.class), x.available(), x.sortOrder(), x.updatedAt());
    }

    /** The storefront view: {@code avatars} and {@code items} already expanded and filtered by the catalog service. */
    ExperienceDto toDto(ExperienceEntity x, List<FamilyDto> avatars, List<CatalogItemDto> items, Long priceFromPaise) {
        return new ExperienceDto(x.id(), x.codename(), x.slug(), x.title(), x.tagline(), x.description(), x.environment(), surface(x),
                x.style(), x.motifPack(), avatars, items, all(x.collections(), ExperienceDto.Collection.class),
                all(x.season(), ExperienceDto.SeasonWindow.class), x.available(), x.sortOrder(), priceFromPaise);
    }

    private ExperienceDto.Surface surface(ExperienceEntity x) {
        return json.convertValue(x.surface(), ExperienceDto.Surface.class);
    }

    private <T> List<T> all(List<Map<String, Object>> documents, Class<T> type) {
        return documents.stream().map(d -> json.convertValue(d, type)).toList();
    }
}
