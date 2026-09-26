package studio.aakar.api.templates;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Public API of the templates module. */
public interface Templates {

    /** All descriptors known to the geometry service (cached). */
    List<TemplateDescriptor> all();

    Optional<TemplateDescriptor> byId(String id);

    /**
     * Validates parameter values against a descriptor. Fails fast with a 422
     * {@code param_out_of_range} problem listing every offending key.
     */
    void validateParams(TemplateDescriptor descriptor, Map<String, Object> params);
}
