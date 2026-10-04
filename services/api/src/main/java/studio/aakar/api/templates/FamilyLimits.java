package studio.aakar.api.templates;

/**
 * What a template's outcome family adds to the feature checks, without the templates module depending on the catalog:
 * the family {@code kind} ({@code raw} makes the single hero form mandatory and sized), the text length cap of its
 * content slot and its size envelope (the longest side a raw print may have).
 *
 * @param maxTextChars {@code content_slot.max_text_chars}, or null for no cap beyond the contract's 40
 * @param minLongestMm / @param maxLongestMm {@code size_envelope_mm}, or null when the family has none
 */
public record FamilyLimits(String id, String kind, Integer maxTextChars, Double minLongestMm, Double maxLongestMm) {

    public static final String KIND_RAW = "raw";

    /** No family rules: only the descriptor and the contract apply. */
    public static FamilyLimits none(String familyId) {
        return new FamilyLimits(familyId, null, null, null, null);
    }

    /** The raw print family (Swaroop): exactly one hero form sized by its longest side. */
    public boolean raw() {
        return KIND_RAW.equals(kind);
    }
}
