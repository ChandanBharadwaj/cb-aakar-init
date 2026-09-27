package studio.aakar.api.templates.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import studio.aakar.api.shared.ApiProblemException;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.templates.FamilyLimits;
import studio.aakar.api.templates.FeatureLabels;
import studio.aakar.api.templates.MotifDto;
import studio.aakar.api.templates.Motifs;
import studio.aakar.api.templates.TemplateDescriptor;

/**
 * Checks content features (the Chhaap) the way the geometry service's {@code features/validate.py} does, so a request
 * the geometry would refuse is answered 422 before a job starts: the feature shape of {@code design-spec.v1.json}
 * (known type, no unknown settings, required settings, types, enums and ranges), then the descriptor
 * ({@code features_supported}, the anchor's {@code accepts}, surface vs volume, what one anchor holds, flat lettering,
 * {@code max_relief_mm} with the lithophane exemption, {@code max_text_height_mm}), then the family (text length without
 * marks, and for the raw family exactly one form sized inside the envelope), the letters of a text ({@link Lettering}) and
 * the motif library ({@link Motifs}). Values are never clamped. Returns the features normalised with the contract
 * defaults, in schema order. Customer-facing {@code detail}s use labels, never code words.
 *
 * <p>What one anchor holds: at most one photo or form, one text (Naam) and one motif (Buti). A text and a motif share a
 * spot side by side; a photo relief fills its spot, so a text or a motif beside it is refused as crowded, whichever
 * came first. Texts and motifs are flat for now: a curved projection is {@code unsupported_feature}.
 *
 * <p>Left to the geometry service, which needs the fonts and the anchor's size for them: characters the bundled fonts
 * lack, and whether a text or a motif fits its spot at a printable stroke. When the motif library is not loaded (a local
 * run without it) motif ids and {@code min_scale} are left to it too.
 */
@Component
class FeatureValidator {

    static final String EMBOSS_TEXT = FeatureLabels.EMBOSS_TEXT;
    static final String MOTIF = FeatureLabels.MOTIF;
    static final String RELIEF_IMAGE = FeatureLabels.RELIEF_IMAGE;
    static final String HERO_MESH = FeatureLabels.HERO_MESH;
    static final List<String> TYPES = List.of(EMBOSS_TEXT, MOTIF, RELIEF_IMAGE, HERO_MESH);
    static final Set<String> SURFACE_TYPES = Set.of(EMBOSS_TEXT, MOTIF, RELIEF_IMAGE);
    /** At most one of these per anchor. */
    static final Set<String> CONTENT_TYPES = Set.of(RELIEF_IMAGE, HERO_MESH);
    /** A text and a motif: one of each per anchor, side by side, never beside a photo; flat lettering only. */
    static final Set<String> MARK_TYPES = Set.of(EMBOSS_TEXT, MOTIF);
    static final String PLANAR = "planar";
    static final Map<String, String> LABELS = FeatureLabels.LABELS;
    static final Map<String, String> DEPTH_KEY = Map.of(RELIEF_IMAGE, "relief_mm", EMBOSS_TEXT, "depth_mm", MOTIF, "depth_mm");
    /** {@code emboss_text.text} maxLength in the contract (code points). */
    static final int TEXT_MAX_CODE_POINTS = 40;
    /** {@code hero_mesh.longest_mm} range in the contract, used when a raw family names no envelope. */
    static final double LONGEST_MIN_MM = 5;
    static final double LONGEST_MAX_MM = 250;
    static final List<String> FORMATS = List.of("png", "jpg", "webp", "heic", "stl", "glb", "3mf", "obj", "ply", "off", "gltf");
    private static final Pattern ID = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final double EPSILON = 1e-9;

    private enum Rule { SOURCE, ANCHOR, STRING, TEXT, ID, ENUM, NUMBER, BOOLEAN }

    /** One setting of a feature type: its rule, whether it is required, its contract default and limits. */
    private record Field(String name, Rule rule, boolean required, Object defaultValue, List<String> options, Double min, Double max,
            String unit, String noun) {
    }

    /** Settings per type in {@code design-spec.v1.json} order. */
    private static final Map<String, List<Field>> FIELDS = Map.of(
            RELIEF_IMAGE, List.of(
                    new Field("source", Rule.SOURCE, true, null, null, null, null, null, "photo"),
                    new Field("anchor", Rule.ANCHOR, true, null, null, null, null, null, "place"),
                    new Field("mode", Rule.ENUM, false, "emboss", List.of("emboss", "deboss", "lithophane"), null, null, null, "style"),
                    new Field("relief_mm", Rule.NUMBER, false, 0.6, null, 0.2, 3.0, "mm", "depth"),
                    new Field("fit", Rule.ENUM, false, "contain", List.of("contain", "cover"), null, null, null, "fit"),
                    new Field("invert", Rule.BOOLEAN, false, false, null, null, null, null, "invert setting"),
                    new Field("cutout", Rule.ENUM, false, "none", List.of("none", "silhouette"), null, null, null, "cut-out")),
            HERO_MESH, List.of(
                    new Field("source", Rule.SOURCE, true, null, null, null, null, null, "model file"),
                    new Field("anchor", Rule.ANCHOR, true, null, null, null, null, null, "place"),
                    new Field("fit", Rule.ENUM, false, "contain", List.of("contain", "longest"), null, null, null, "fit"),
                    new Field("longest_mm", Rule.NUMBER, false, null, null, LONGEST_MIN_MM, LONGEST_MAX_MM, "mm", "longest side"),
                    new Field("yaw_deg", Rule.NUMBER, false, 0, null, 0.0, 360.0, "°", "turn"),
                    new Field("orientation", Rule.ENUM, false, "as_uploaded", List.of("as_uploaded", "lay_flat"), null, null, null, "orientation")),
            EMBOSS_TEXT, List.of(
                    new Field("text", Rule.TEXT, true, null, null, null, null, null, "text"),
                    new Field("script", Rule.ENUM, false, null, List.of("latin", "devanagari", "telugu", "tamil", "kannada", "bengali", "gujarati"),
                            null, null, null, "script"),
                    new Field("font", Rule.STRING, false, null, null, null, null, null, "font"),
                    new Field("depth_mm", Rule.NUMBER, false, 1.2, null, 0.4, 3.0, "mm", "depth"),
                    new Field("height_mm", Rule.NUMBER, false, null, null, 4.0, 60.0, "mm", "letter height"),
                    new Field("anchor", Rule.ANCHOR, true, null, null, null, null, null, "place"),
                    new Field("projection", Rule.ENUM, false, "planar", List.of("planar", "cylindrical", "conformal"), null, null, null, "projection"),
                    new Field("mode", Rule.ENUM, false, "emboss", List.of("emboss", "deboss"), null, null, null, "style")),
            MOTIF, List.of(
                    new Field("motif_id", Rule.ID, true, null, null, null, null, null, "motif"),
                    new Field("anchor", Rule.ANCHOR, true, null, null, null, null, null, "place"),
                    new Field("scale", Rule.NUMBER, false, 1, null, 0.2, 1.0, "", "scale"),
                    new Field("depth_mm", Rule.NUMBER, false, 1.0, null, 0.4, 3.0, "mm", "depth"),
                    new Field("mode", Rule.ENUM, false, "deboss", List.of("emboss", "deboss"), null, null, null, "style")));
    private static final List<String> SOURCE_KEYS = List.of("upload_id", "url", "format", "origin", "provider");

    private final Motifs motifs;

    FeatureValidator(Motifs motifs) {
        this.motifs = motifs;
    }

    List<Map<String, Object>> validate(TemplateDescriptor descriptor, FamilyLimits family, List<Map<String, Object>> requested) {
        FamilyLimits limits = family == null ? FamilyLimits.none(descriptor.family()) : family;
        List<Map<String, Object>> features = requested == null ? List.of() : requested;

        List<Map<String, Object>> normalised = new ArrayList<>();
        for (int i = 0; i < features.size(); i++) {
            normalised.add(normalise(descriptor, i, features.get(i)));
        }

        List<String> supported = descriptor.featuresSupportedOrEmpty();
        List<String> unsupported = normalised.stream().map(f -> (String) f.get("type")).filter(t -> !supported.contains(t)).distinct().toList();
        if (!unsupported.isEmpty()) {
            throw problem(ProblemCodes.UNSUPPORTED_FEATURE, "Unsupported feature",
                    capitalise(descriptor.name()) + " can't carry " + unsupported.stream().map(LABELS::get).collect(Collectors.joining(" or ")) + " yet",
                    descriptor, null, Map.of("unsupported", unsupported, "features_supported", supported));
        }

        Map<String, TemplateDescriptor.Anchor> anchors = new LinkedHashMap<>();
        descriptor.anchors().forEach(a -> anchors.put(a.id(), a));
        Map<String, List<String>> onAnchor = new HashMap<>();
        for (int i = 0; i < normalised.size(); i++) {
            checkPlacement(descriptor, limits, anchors, onAnchor, i, normalised.get(i));
        }
        if (limits.raw()) {
            checkRaw(descriptor, limits, normalised);
        }
        return normalised;
    }

    // ---- shape of one feature -----------------------------------------------------------------------------------------

    private Map<String, Object> normalise(TemplateDescriptor descriptor, int index, Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", "Each piece of content needs a type and a place", descriptor, index,
                    Map.of());
        }
        if (!(map.get("type") instanceof String type) || !TYPES.contains(type)) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed",
                    "Only text (Naam), a motif (Buti), a photo relief (Chhavi) or your own 3D form (Roop) can be added", descriptor, index,
                    map.get("type") == null ? Map.of() : Map.of("type", String.valueOf(map.get("type"))));
        }
        String label = LABELS.get(type);
        List<Field> fields = FIELDS.get(type);
        Set<String> known = fields.stream().map(Field::name).collect(Collectors.toCollection(LinkedHashSet::new));
        known.add("type");
        List<String> unknown = map.keySet().stream().map(String::valueOf).filter(k -> !known.contains(k)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", capitalise(label) + " has settings we don't recognise", descriptor, index,
                    Map.of("fields", unknown));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        for (Field field : fields) {
            Object value = map.get(field.name());
            if (value == null) {
                if (field.required()) {
                    throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", missing(type, field), descriptor, index,
                            Map.of("field", path(index, field.name())));
                }
                if (field.defaultValue() != null) {
                    out.put(field.name(), field.defaultValue());
                }
                continue;
            }
            out.put(field.name(), checkValue(descriptor, index, type, field, value));
        }
        return out;
    }

    private Object checkValue(TemplateDescriptor descriptor, int index, String type, Field field, Object value) {
        String label = LABELS.get(type);
        String path = path(index, field.name());
        return switch (field.rule()) {
            case SOURCE -> checkSource(descriptor, index, type, value);
            case ANCHOR, STRING -> {
                if (!(value instanceof String s) || (field.rule() == Rule.ANCHOR && s.isBlank())) {
                    throw invalid(descriptor, index, path, "The " + field.noun() + " of the " + label + " must be text");
                }
                yield s;
            }
            case TEXT -> {
                if (!(value instanceof String s) || s.isBlank()) {
                    throw invalid(descriptor, index, path, missing(type, field));
                }
                int length = s.codePointCount(0, s.length());
                if (length > TEXT_MAX_CODE_POINTS) {
                    throw outOfRange(descriptor, index, path, capitalise(label) + " can be at most " + TEXT_MAX_CODE_POINTS + " characters; "
                            + length + " were given");
                }
                yield s;
            }
            case ID -> {
                if (!(value instanceof String s) || !ID.matcher(s).matches()) {
                    throw invalid(descriptor, index, path, "That " + field.noun() + " isn't one we know");
                }
                yield s;
            }
            case ENUM -> {
                if (!(value instanceof String s) || !field.options().contains(s)) {
                    throw invalid(descriptor, index, path, "The " + field.noun() + " of the " + label + " must be one of "
                            + String.join(", ", field.options()));
                }
                yield s;
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean b)) {
                    throw invalid(descriptor, index, path, "The " + field.noun() + " of the " + label + " must be true or false");
                }
                yield b;
            }
            case NUMBER -> {
                if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                    throw invalid(descriptor, index, path, "The " + field.noun() + " of the " + label + " must be a number");
                }
                double v = n.doubleValue();
                if ((field.min() != null && v < field.min() - EPSILON) || (field.max() != null && v > field.max() + EPSILON)) {
                    throw outOfRange(descriptor, index, path, "The " + field.noun() + " of the " + label + " can be " + fmt(field.min()) + "–"
                            + fmt(field.max()) + unit(field) + "; " + fmt(v) + unit(field) + " was asked");
                }
                yield n;
            }
        };
    }

    /** {@code content_source}: an upload id (the API fills the url, format and origin from the upload itself). */
    private Map<String, Object> checkSource(TemplateDescriptor descriptor, int index, String type, Object value) {
        String path = path(index, "source");
        String missing = RELIEF_IMAGE.equals(type) ? "Add a photo for the photo relief (Chhavi)" : "Add your model file for your own 3D form (Roop)";
        if (!(value instanceof Map<?, ?> source)) {
            throw invalid(descriptor, index, path, missing);
        }
        List<String> unknown = source.keySet().stream().map(String::valueOf).filter(k -> !SOURCE_KEYS.contains(k)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", "The file reference of the " + LABELS.get(type)
                    + " has settings we don't recognise", descriptor, index, Map.of("fields", unknown));
        }
        if (!(source.get("upload_id") instanceof String uploadId) || uploadId.isBlank()) {
            throw invalid(descriptor, index, path + ".upload_id", missing);
        }
        try {
            UUID.fromString(uploadId.trim());
        } catch (IllegalArgumentException e) {
            throw invalid(descriptor, index, path + ".upload_id", "That file reference isn't valid; upload the file again");
        }
        Object url = source.get("url");
        Object format = source.get("format");
        Object origin = source.get("origin");
        Object provider = source.get("provider");
        if ((url != null && !(url instanceof String)) || (provider != null && !(provider instanceof String))
                || (format != null && !FORMATS.contains(String.valueOf(format))) || (origin != null && !List.of("upload", "generated").contains(String.valueOf(origin)))) {
            throw invalid(descriptor, index, path, "The file reference of the " + LABELS.get(type) + " isn't valid; upload the file again");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("upload_id", uploadId.trim());
        if (url != null) {
            out.put("url", url);
        }
        if (format != null) {
            out.put("format", format);
        }
        out.put("origin", origin == null ? "upload" : origin);
        if (provider != null) {
            out.put("provider", provider);
        }
        return out;
    }

    // ---- where it goes ------------------------------------------------------------------------------------------------

    private void checkPlacement(TemplateDescriptor descriptor, FamilyLimits limits, Map<String, TemplateDescriptor.Anchor> anchors,
            Map<String, List<String>> onAnchor, int index, Map<String, Object> feature) {
        String type = (String) feature.get("type");
        String label = LABELS.get(type);
        String anchorId = (String) feature.get("anchor");
        TemplateDescriptor.Anchor anchor = anchors.get(anchorId);
        if (anchor == null) {
            String places = descriptor.anchors().stream().map(a -> labelOf(a)).collect(Collectors.joining(", "));
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", capitalise(descriptor.name()) + " has no such place for the " + label
                    + (places.isEmpty() ? "" : "; choose one of " + places), descriptor, index, Map.of("field", path(index, "anchor")));
        }
        List<String> accepts = anchor.accepts() != null ? anchor.accepts() : descriptor.featuresSupportedOrEmpty();
        if (!accepts.contains(type)) {
            throw problem(ProblemCodes.UNSUPPORTED_FEATURE, "Unsupported feature", "The " + labelOf(anchor) + " of " + descriptor.name()
                    + " does not take " + label, descriptor, index, Map.of("anchor", anchor.id(), "accepts", accepts));
        }
        boolean volume = anchor.isVolume();
        if (HERO_MESH.equals(type) && !volume) {
            throw problem(ProblemCodes.UNSUPPORTED_FEATURE, "Unsupported feature", "The " + labelOf(anchor)
                    + " is a surface; your own 3D form (Roop) needs a place with room for it", descriptor, index, Map.of("anchor", anchor.id()));
        }
        if (SURFACE_TYPES.contains(type) && volume) {
            throw problem(ProblemCodes.UNSUPPORTED_FEATURE, "Unsupported feature", "The " + labelOf(anchor) + " holds a 3D form; " + label
                    + " needs a flat surface", descriptor, index, Map.of("anchor", anchor.id()));
        }
        checkCompany(descriptor, anchor, onAnchor.computeIfAbsent(anchor.id(), id -> new ArrayList<>()), index, type);
        if (MARK_TYPES.contains(type)) {
            checkFlat(descriptor, anchor, index, feature);
        }
        if (HERO_MESH.equals(type) && "longest".equals(feature.get("fit")) && feature.get("longest_mm") == null) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", "Choose how long your own 3D form (Roop) should be on its longest side",
                    descriptor, index, Map.of("field", path(index, "longest_mm")));
        }
        if (RELIEF_IMAGE.equals(type) && !"none".equals(feature.get("cutout"))) {
            throw problem(ProblemCodes.UNSUPPORTED_FEATURE, "Unsupported feature",
                    "Cut-out silhouettes are not available yet; the photo relief (Chhavi) stays on its plate", descriptor, index, Map.of("field",
                            path(index, "cutout")));
        }
        String depthKey = DEPTH_KEY.get(type);
        // A lithophane is the plate itself (light through 0.8–3 mm), not a relief on its skin: the template owns that range.
        boolean lithophane = RELIEF_IMAGE.equals(type) && "lithophane".equals(feature.get("mode"));
        if (depthKey != null && anchor.maxReliefMm() != null && !lithophane && feature.get(depthKey) instanceof Number depth
                && depth.doubleValue() > anchor.maxReliefMm() + EPSILON) {
            throw outOfRange(descriptor, index, path(index, depthKey), "The " + label + " on the " + labelOf(anchor) + " can be at most "
                    + fmt(anchor.maxReliefMm()) + " mm deep; " + fmt(depth.doubleValue()) + " mm was asked");
        }
        if (EMBOSS_TEXT.equals(type) && limits.maxTextChars() != null) {
            int length = textLength((String) feature.get("text"));
            if (length > limits.maxTextChars()) {
                throw outOfRange(descriptor, index, path(index, "text"), "Text (Naam) on the " + labelOf(anchor) + " can be at most "
                        + limits.maxTextChars() + " characters; " + length + " were given");
            }
        }
        if (EMBOSS_TEXT.equals(type)) {
            checkLettering(descriptor, anchor, index, feature);
        }
        if (MOTIF.equals(type)) {
            checkMotif(descriptor, index, feature);
        }
    }

    /**
     * What one anchor holds, given what the features before this one put there ({@code seen}, which this one joins): one
     * photo or form, one text, one motif, and never a photo beside a text or a motif.
     */
    private static void checkCompany(TemplateDescriptor descriptor, TemplateDescriptor.Anchor anchor, List<String> seen, int index, String type) {
        String place = labelOf(anchor);
        if (CONTENT_TYPES.contains(type) && seen.stream().anyMatch(CONTENT_TYPES::contains)) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", "Only one photo or 3D form can go on the " + place,
                    descriptor, index, Map.of("anchor", anchor.id()));
        }
        if (MARK_TYPES.contains(type) && seen.contains(type)) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", "Only one " + LABELS.get(type) + " can go on the " + place
                    + "; put the other one on another spot", descriptor, index, Map.of("anchor", anchor.id()));
        }
        // a photo fills its spot: a text or a motif beside it is refused as crowded, whichever came first
        String crowding = RELIEF_IMAGE.equals(type) ? seen.stream().filter(MARK_TYPES::contains).findFirst().orElse(null)
                : MARK_TYPES.contains(type) && seen.contains(RELIEF_IMAGE) ? type : null;
        if (crowding != null) {
            String other = LABELS.get(crowding);
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", "The " + place + " is too crowded for a photo relief (Chhavi) and "
                    + (MOTIF.equals(crowding) ? "a " : "") + other + " together; put the " + other + " on another spot", descriptor, index,
                    Map.of("anchor", anchor.id()));
        }
        seen.add(type);
    }

    /** No anchor is curved yet, so a text or a motif is set flat: a curved projection (or a curved anchor) is not built. */
    private static void checkFlat(TemplateDescriptor descriptor, TemplateDescriptor.Anchor anchor, int index, Map<String, Object> feature) {
        String type = (String) feature.get("type");
        String projection = EMBOSS_TEXT.equals(type) ? String.valueOf(feature.get("projection")) : PLANAR;
        String anchorProjection = anchor.projection() == null ? PLANAR : anchor.projection();
        if (PLANAR.equals(projection) && PLANAR.equals(anchorProjection)) {
            return;
        }
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("anchor", anchor.id());
        if (!PLANAR.equals(projection)) {
            where.put("field", path(index, "projection"));
        }
        String what = EMBOSS_TEXT.equals(type) ? "Text (Naam) that wraps" : "A motif (Buti) that wraps";
        throw problem(ProblemCodes.UNSUPPORTED_FEATURE, "Unsupported feature", what + " around a curved surface is not available yet; every spot "
                + "today is flat", descriptor, index, where);
    }

    /** The letters ({@link Lettering}: one line, one launch script, the bundled lettering) and their height on this anchor. */
    private static void checkLettering(TemplateDescriptor descriptor, TemplateDescriptor.Anchor anchor, int index, Map<String, Object> feature) {
        Lettering.Refusal refusal = Lettering.check((String) feature.get("text"), (String) feature.get("script"), (String) feature.get("font"));
        if (refusal != null) {
            boolean unsupported = ProblemCodes.UNSUPPORTED_FEATURE.equals(refusal.code());
            throw problem(refusal.code(), unsupported ? "Unsupported feature" : "Validation failed", refusal.detail(), descriptor, index,
                    Map.of("field", path(index, refusal.field())));
        }
        if (anchor.maxTextHeightMm() != null && feature.get("height_mm") instanceof Number height
                && height.doubleValue() > anchor.maxTextHeightMm() + EPSILON) {
            throw outOfRange(descriptor, index, path(index, "height_mm"), "Text (Naam) on the " + labelOf(anchor) + " can be at most "
                    + fmt(anchor.maxTextHeightMm()) + " mm tall; " + fmt(height.doubleValue()) + " mm was asked");
        }
    }

    /** The motif must be in the library, at a scale it still prints at: from its {@code min_scale} up to 1, which fills the spot. */
    private void checkMotif(TemplateDescriptor descriptor, int index, Map<String, Object> feature) {
        if (!motifs.available()) {
            return; // a local run without the library: the geometry service still checks both
        }
        String motifId = (String) feature.get("motif_id");
        MotifDto motif = motifs.find(motifId).orElseThrow(() -> invalid(descriptor, index, path(index, "motif_id"),
                "We don't have a motif (Buti) called “" + motifId + "”; choose one from the motif library"));
        double scale = ((Number) feature.get("scale")).doubleValue();
        if (scale < motif.minScale() - EPSILON) {
            throw outOfRange(descriptor, index, path(index, "scale"), "The " + motif.label() + " motif (Buti) can't be printed smaller than scale "
                    + fmt(motif.minScale()) + "; choose a larger scale");
        }
    }

    /** The raw family (Swaroop): the customer's model is the whole piece, so exactly one form, sized explicitly inside the envelope. */
    private void checkRaw(TemplateDescriptor descriptor, FamilyLimits limits, List<Map<String, Object>> features) {
        List<Integer> heroes = new ArrayList<>();
        for (int i = 0; i < features.size(); i++) {
            if (HERO_MESH.equals(features.get(i).get("type"))) {
                heroes.add(i);
            }
        }
        double lo = limits.minLongestMm() == null ? LONGEST_MIN_MM : limits.minLongestMm();
        double hi = limits.maxLongestMm() == null ? LONGEST_MAX_MM : limits.maxLongestMm();
        String range = fmt(lo) + "–" + fmt(hi) + " mm";
        if (heroes.isEmpty()) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", "Add your model file to print it as it is", descriptor, null,
                    Map.of("needs", "one " + HERO_MESH + " feature"));
        }
        if (heroes.size() > 1) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", "Print one model file at a time", descriptor, heroes.get(1), Map.of());
        }
        int index = heroes.get(0);
        Map<String, Object> hero = features.get(index);
        if (!"longest".equals(hero.get("fit"))) {
            throw problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", "Choose how long your model should be on its longest side (" + range + ")",
                    descriptor, index, Map.of("field", path(index, "fit")));
        }
        double longest = ((Number) hero.get("longest_mm")).doubleValue();
        if (longest < lo - EPSILON || longest > hi + EPSILON) {
            throw outOfRange(descriptor, index, path(index, "longest_mm"), "Your model can be printed " + range + " on its longest side; "
                    + fmt(longest) + " mm was asked");
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    /**
     * Characters as a reader counts them: combining marks (Indic vowel signs and viramas, accents) and format characters
     * (zero-width joiners) do not add, so "नमस्ते" counts 4. Unicode general categories Mn, Mc, Me and Cf are left out.
     */
    static int textLength(String text) {
        if (text == null) {
            return 0;
        }
        return (int) text.codePoints().filter(cp -> {
            int category = Character.getType(cp);
            return category != Character.NON_SPACING_MARK && category != Character.COMBINING_SPACING_MARK
                    && category != Character.ENCLOSING_MARK && category != Character.FORMAT;
        }).count();
    }

    private static String missing(String type, Field field) {
        return switch (field.name()) {
            case "anchor" -> "Choose where the " + LABELS.get(type) + " goes";
            case "text" -> "Type the text (Naam) to add";
            case "motif_id" -> "Choose a motif (Buti)";
            default -> RELIEF_IMAGE.equals(type) ? "Add a photo for the photo relief (Chhavi)" : "Add your model file for your own 3D form (Roop)";
        };
    }

    private static String labelOf(TemplateDescriptor.Anchor anchor) {
        return anchor.label() == null || anchor.label().isBlank() ? anchor.id() : anchor.label();
    }

    private static String path(int index, String field) {
        return "features[" + index + "]." + field;
    }

    private static String unit(Field field) {
        if (field.unit() == null || field.unit().isEmpty()) {
            return "";
        }
        return "°".equals(field.unit()) ? "°" : " " + field.unit();
    }

    private static ApiProblemException invalid(TemplateDescriptor descriptor, int index, String path, String detail) {
        return problem(ProblemCodes.VALIDATION_FAILED, "Validation failed", detail, descriptor, index, Map.of("field", path));
    }

    /** 422 {@code param_out_of_range}, listing the offending path like a template parameter. */
    private static ApiProblemException outOfRange(TemplateDescriptor descriptor, int index, String path, String detail) {
        return problem(ProblemCodes.PARAM_OUT_OF_RANGE, "Parameter out of range", detail, descriptor, index, Map.of("params", List.of(path)));
    }

    private static ApiProblemException problem(String code, String title, String detail, TemplateDescriptor descriptor, Integer index,
            Map<String, Object> extra) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("template_id", descriptor.id());
        if (index != null) {
            properties.put("feature", index);
        }
        properties.putAll(extra);
        return ApiProblemException.unprocessable(code, title, detail, properties);
    }

    private static String capitalise(String text) {
        return text == null || text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private static String fmt(Double d) {
        if (d == null) {
            return "…";
        }
        return d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
    }
}
