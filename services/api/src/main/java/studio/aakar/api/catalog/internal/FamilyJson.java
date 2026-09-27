package studio.aakar.api.catalog.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import studio.aakar.api.catalog.FamilyDto;
import studio.aakar.api.catalog.HardwareRef;
import studio.aakar.api.templates.TemplateDescriptor;

/**
 * Converts between the JSONB documents on {@link TemplateFamilyEntity} (snake_case maps, exactly as seeded from
 * {@code families.json}) and the typed records of {@link FamilyDto}, using the application's snake_case
 * {@link ObjectMapper} so the stored keys are the contract's.
 */
@Component
class FamilyJson {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> MAPS = new TypeReference<>() { };

    private final ObjectMapper json;

    FamilyJson(ObjectMapper json) {
        this.json = json;
    }

    Map<String, Object> toMap(Object value) {
        return value == null ? null : json.convertValue(value, MAP);
    }

    List<Map<String, Object>> toMaps(List<?> values) {
        return values == null ? List.of() : json.convertValue(values, MAPS);
    }

    <T> T from(Map<String, Object> document, Class<T> type) {
        return document == null ? null : json.convertValue(document, type);
    }

    /**
     * The row plus its readiness: {@code templates} are the live descriptors whose {@code family} is this id;
     * {@code priceFromPaise} is the family's minimum subtotal under the active policy (a floor), or null.
     */
    FamilyDto toDto(TemplateFamilyEntity f, Map<String, String> hardwareNames, List<TemplateDescriptor> templates, Long priceFromPaise) {
        List<HardwareRef> hardware = f.hardware().stream()
                .map(ref -> from(ref, HardwareRef.class))
                .map(ref -> ref.named(hardwareNames.get(ref.sku())))
                .toList();
        return new FamilyDto(f.id(), f.codename(), f.name(), f.tagline(), f.description(), f.kind(), f.tier(), f.shelf(), f.demandRank(),
                f.defaultTemplateId(), f.environment(), from(f.sizeEnvelope(), FamilyDto.SizeEnvelope.class), hardware,
                from(f.materialRules(), FamilyDto.MaterialRules.class), f.shapeTolerance(), from(f.contentSlot(), FamilyDto.ContentSlot.class),
                f.available(), f.sortOrder(), !templates.isEmpty(), templates, templates.stream().map(TemplateDescriptor::id).toList(),
                priceFromPaise, f.updatedAt());
    }
}
