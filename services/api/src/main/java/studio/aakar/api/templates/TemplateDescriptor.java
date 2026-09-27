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
 * Since the outcome-category work a descriptor also names the bought-in {@link #hardware()} its pockets and
 * slots are cut for (authoritative over the family default) and its smallest printable detail
 * ({@link #minFeatureMm()}); anchors say what content they take and how large it may be (the Chhaap).
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
        List<String> featuresSupported,
        List<Hardware> hardware,
        Double minFeatureMm) {

    public TemplateDescriptor {
        params = params == null ? Map.of() : Map.copyOf(params);
        anchors = anchors == null ? List.of() : List.copyOf(anchors);
        materials = materials == null ? List.of() : List.copyOf(materials);
        hardware = hardware == null ? List.of() : List.copyOf(hardware);
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

    /** Feature types the template supports; an absent {@code features_supported} means none. */
    public List<String> featuresSupportedOrEmpty() {
        return featuresSupported == null ? List.of() : featuresSupported;
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

    /**
     * A named place where content may land: a {@code surface} (text, motif, relief) with a printable
     * {@code size_mm} {@code [w, h]} and a {@code bleed_mm} safe margin, or a {@code volume} (a customer's own 3D form)
     * with {@code bounds_mm} {@code [w, d, h]}. {@code accepts} narrows the template's {@code features_supported} for
     * this anchor; {@code max_relief_mm} caps relief and emboss depth. {@code kind} defaults to {@code surface}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Anchor(
            String id,
            String label,
            String kind,
            String projection,
            Double maxTextHeightMm,
            List<Double> sizeMm,
            Double bleedMm,
            List<Double> boundsMm,
            List<String> accepts,
            Double maxReliefMm) {

        public static final String SURFACE = "surface";
        public static final String VOLUME = "volume";

        public boolean isVolume() {
            return VOLUME.equals(kind);
        }
    }

    /** A bought-in part ({@code hardware_items.sku}) packed with every piece made from this template. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hardware(String sku, int qty) {
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
