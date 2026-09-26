package studio.aakar.api.templates;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code template-descriptor.v1.json}: what a parametric template publishes about itself. Returned
 * verbatim by {@code GET /api/templates}; {@code null} members are omitted so the JSON round-trips.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplateDescriptor(
        String id,
        int version,
        String family,
        String name,
        String description,
        String environment,
        Map<String, Param> params,
        List<Anchor> anchors,
        Constraints constraints,
        List<String> materials,
        List<String> styleVariants,
        List<String> featuresSupported) {

    public TemplateDescriptor {
        params = params == null ? Map.of() : Map.copyOf(params);
        anchors = anchors == null ? List.of() : List.copyOf(anchors);
        materials = materials == null ? List.of() : List.copyOf(materials);
    }

    /** {@code id@version}, the form used in a Design Spec. */
    public String ref() {
        return id + "@" + version;
    }

    /** Default value of every parameter, in descriptor order. */
    public Map<String, Object> defaultParams() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        params.forEach((key, p) -> defaults.put(key, p.defaultValue()));
        return defaults;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Param(
            String type,
            String label,
            String description,
            String unit,
            @JsonProperty("default") Object defaultValue,
            Double min,
            Double max,
            Double step,
            List<String> options,
            Boolean handle,
            String group) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Anchor(String id, String label, String projection, Double maxTextHeightMm) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Constraints(Double minWallMm, Double maxOverhangDeg, List<Double> bedMm) {

        /** The constraints block of a Design Spec ({@code design-spec.v1.json#/$defs/constraints}). */
        public Map<String, Object> toSpecConstraints() {
            Map<String, Object> c = new LinkedHashMap<>();
            if (minWallMm != null) {
                c.put("min_wall_mm", minWallMm);
            }
            if (maxOverhangDeg != null) {
                c.put("max_overhang_deg", maxOverhangDeg);
            }
            if (bedMm != null) {
                c.put("bed_mm", List.copyOf(bedMm));
            }
            return c;
        }
    }
}
