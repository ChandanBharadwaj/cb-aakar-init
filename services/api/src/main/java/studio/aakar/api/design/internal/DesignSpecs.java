package studio.aakar.api.design.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import studio.aakar.api.templates.TemplateDescriptor;

/** Builds and reads {@code design-spec.v1.json} documents. */
final class DesignSpecs {

    static final String SPEC_VERSION = "1.0";
    static final String STYLE_NONE = "none";

    private DesignSpecs() {
    }

    /**
     * @param features content features already validated and normalised (contract defaults filled) with their
     *                 {@code content_source} resolved from the uploads (url, format, origin); an empty list for none
     */
    static Map<String, Object> build(TemplateDescriptor descriptor, Map<String, Object> params, String material, List<Map<String, Object>> features) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("spec_version", SPEC_VERSION);
        spec.put("family", descriptor.family());
        spec.put("template", descriptor.ref());
        spec.put("params", new LinkedHashMap<>(params));
        spec.put("features", features == null ? List.of() : copy(features));
        spec.put("style", STYLE_NONE);
        spec.put("material", material);
        Map<String, Object> constraints = descriptor.constraints() == null ? Map.of() : descriptor.constraints().toSpecConstraints();
        if (!constraints.isEmpty()) {
            spec.put("constraints", constraints);
        }
        return spec;
    }

    /** {@code {id, version}} from {@code id@version}. */
    static Map<String, Object> templateRef(TemplateDescriptor descriptor) {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("id", descriptor.id());
        ref.put("version", descriptor.version());
        return ref;
    }

    static String templateId(Map<String, Object> spec) {
        Object template = spec == null ? null : spec.get("template");
        if (!(template instanceof String s) || s.isBlank()) {
            return null;
        }
        int at = s.indexOf('@');
        return at < 0 ? s : s.substring(0, at);
    }

    /** {@code spec.family}, the outcome family id; null when absent. */
    static String family(Map<String, Object> spec) {
        Object family = spec == null ? null : spec.get("family");
        return family instanceof String s && !s.isBlank() ? s : null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> params(Map<String, Object> spec) {
        Object params = spec == null ? null : spec.get("params");
        return params instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** {@code spec.features} as stored (normalised), in order; empty when absent. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> features(Map<String, Object> spec) {
        Object features = spec == null ? null : spec.get("features");
        if (!(features instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object feature : list) {
            if (feature instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    /** Every {@code content_source.upload_id} the spec's features name. */
    static Set<UUID> uploadIds(Map<String, Object> spec) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Map<String, Object> feature : features(spec)) {
            if (feature.get("source") instanceof Map<?, ?> source && source.get("upload_id") instanceof String id) {
                try {
                    ids.add(UUID.fromString(id));
                } catch (IllegalArgumentException e) {
                    // a malformed id in an old spec names no upload
                }
            }
        }
        return ids;
    }

    static String material(Map<String, Object> spec) {
        Object material = spec == null ? null : spec.get("material");
        return material instanceof String s && !s.isBlank() ? s : null;
    }

    /** {@code overrides} on top of {@code defaults}, keeping the defaults' key order. */
    static Map<String, Object> merge(Map<String, Object> defaults, Map<String, Object> overrides) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults == null ? Map.of() : defaults);
        if (overrides != null) {
            merged.putAll(overrides);
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> copy(List<Map<String, Object>> features) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> feature : features) {
            Map<String, Object> copy = new LinkedHashMap<>();
            feature.forEach((key, value) -> copy.put(key, value instanceof Map<?, ?> nested ? new LinkedHashMap<>((Map<String, Object>) nested) : value));
            out.add(copy);
        }
        return out;
    }
}
