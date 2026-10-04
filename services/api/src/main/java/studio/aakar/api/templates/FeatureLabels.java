package studio.aakar.api.templates;

import java.util.Map;

/**
 * Customer labels of the content feature types (the Chhaap): the words problem details, print sheets and copy use,
 * never the code words of {@code design-spec.v1.json}. Lowercase, for use inside a sentence.
 */
public final class FeatureLabels {

    public static final String EMBOSS_TEXT = "emboss_text";
    public static final String MOTIF = "motif";
    public static final String RELIEF_IMAGE = "relief_image";
    public static final String HERO_MESH = "hero_mesh";

    /** Feature type → label. */
    public static final Map<String, String> LABELS = Map.of(
            EMBOSS_TEXT, "text (Naam)",
            MOTIF, "motif (Buti)",
            RELIEF_IMAGE, "photo relief (Chhavi)",
            HERO_MESH, "your own 3D form (Roop)");

    private FeatureLabels() {
    }

    /** The label of a feature type, or a neutral phrase for an unknown one. */
    public static String of(String type) {
        return type == null ? "content" : LABELS.getOrDefault(type, "content");
    }
}
