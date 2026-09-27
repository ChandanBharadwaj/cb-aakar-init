package studio.aakar.api.templates;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Public API of the templates module. */
public interface Templates {

    /** Live descriptors: everything the geometry service publishes minus the templates staff switched off (cached). */
    List<TemplateDescriptor> all();

    /** Every descriptor the geometry service publishes, live or not (management API). */
    List<TemplateDescriptor> allKnown();

    /** Any known descriptor by id, live or not — existing designs keep resolving their template. */
    Optional<TemplateDescriptor> byId(String id);

    /** {@code false} once staff switched the template off; new designs from it answer 422 {@code template_not_available}. */
    boolean isLive(String templateId);

    /** Explicit flags stored so far (a template without a row is live). */
    Map<String, Boolean> liveFlags();

    /** Switches a template on or off; 404 for an id the geometry service does not publish. */
    TemplateDescriptor setLive(String templateId, boolean live);

    /**
     * Validates parameter values against a descriptor. Fails fast with a 422
     * {@code param_out_of_range} problem listing every offending key.
     */
    void validateParams(TemplateDescriptor descriptor, Map<String, Object> params);
}
