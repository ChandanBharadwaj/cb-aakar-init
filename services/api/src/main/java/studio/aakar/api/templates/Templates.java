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

    /**
     * Validates content features (the Chhaap: {@code design-spec.v1.json#/$defs/feature}) against a descriptor and its
     * family, mirroring the geometry service's checks, and returns them normalised with the contract defaults. The type
     * must be in {@code features_supported}; the anchor must exist and accept it (text, motifs and photo reliefs on
     * surfaces, a customer's own form on a volume); an anchor holds at most one photo or form, one text and one motif, and
     * a photo never shares it with a text or a motif (crowded); texts and motifs are flat ({@code planar} only); relief and
     * emboss depth within the anchor's {@code max_relief_mm} (a lithophane relief is exempt) and letter height within its
     * {@code max_text_height_mm}; text within the family's {@code max_text_chars} (marks and joiners do not count), on one
     * line, in one of the seven launch scripts (matching {@code script} when given) and in the bundled lettering when a
     * {@code font} is named; a motif from the {@link Motifs} library at a scale between its {@code min_scale} and 1; a raw
     * family needs exactly one form sized by its longest side inside the family envelope. An anchor that lists
     * {@code modes} takes only those ({@code unsupported_feature} on {@code features[i].mode}; a feature that leaves its mode
     * out takes its type's default), and an anchor marked {@code required} must hold content ({@code validation_failed}).
     * Values are rejected, never clamped: 422 {@code validation_failed}, {@code unsupported_feature} or
     * {@code param_out_of_range} (with the offending {@code features[i].field} path in {@code params}); the {@code detail}
     * uses customer labels ("photo relief (Chhavi)"), never code words.
     *
     * <p>Under the spec style {@code comic_pop} (Katha) the defaults are the geometry service's comic ones: a text or a motif
     * that leaves its mode out is raised where the anchor allows it, and a raised text or motif that leaves its depth out
     * stands 1.5 mm proud, capped at the anchor's {@code max_relief_mm}. Explicit choices and photos are left alone.
     *
     * @param family the family's limits, or {@link FamilyLimits#none(String)}
     * @param features the requested features (may be empty); {@code content_source.url} is not required
     * @param style the design's spec style ({@code none}, {@code comic_pop}, …); null counts as {@code none}
     */
    List<Map<String, Object>> validateFeatures(TemplateDescriptor descriptor, FamilyLimits family, List<Map<String, Object>> features, String style);
}
