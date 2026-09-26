package studio.aakar.api.design.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import studio.aakar.api.templates.TemplateDescriptor;

/** Builds and reads {@code design-spec.v1.json} documents. */
final class DesignSpecs {

    static final String SPEC_VERSION = "1.0";
    static final String STYLE_NONE = "none";

    private DesignSpecs() {
    }

    static Map<String, Object> build(TemplateDescriptor descriptor, Map<String, Object> params, String material) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("spec_version", SPEC_VERSION);
        spec.put("family", descriptor.family());
        spec.put("template", descriptor.ref());
        spec.put("params", new LinkedHashMap<>(params));
        spec.put("features", List.of());
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

    @SuppressWarnings("unchecked")
    static Map<String, Object> params(Map<String, Object> spec) {
        Object params = spec == null ? null : spec.get("params");
        return params instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
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
}
