package studio.aakar.api.media.internal;

import java.text.Normalizer;
import java.util.BitSet;

/**
 * How the content guardrail compares words (Katha, plan §8), for file names and for the text (Naam) of a design alike.
 *
 * <p>Case, spaces and punctuation never count: text and term are folded (NFKD, so accents, full-width letters and
 * ligatures fold: "Bätman", "ＢＡＴＭＡＮ"), lowercased and reduced to letters and digits, so "iron man", "Iron-Man" and
 * "IRONMAN" are one term. Marks (Indic vowel signs and viramas, accents) and joiners are dropped without breaking a word.
 *
 * <p>A term longer than {@value #WHOLE_WORD_MAX} letters and digits may sit anywhere in the text, even run into other words
 * ("Marvel_IronMan-poster.png"). A shorter one ("DC", "Thor", "Marvel", "Batman") must start where a word of the text starts
 * and end where a word ends, so it never matches inside a longer word: "Adcock", "Thorat", "Author", "Marvellous" and
 * "Nagrajan" pass. Spacing and punctuation inside it still don't count ("Bat Man", "D.C."). Words break at anything that is
 * not a letter, a digit or a mark, where lowercase turns to uppercase ("ThorHammer"), before the last capital of a run of
 * capitals that starts a word ("DCComics") and between letters and digits ("DC2024").
 */
final class TermMatcher {

    /** Terms of this many letters and digits or fewer only match as whole words. */
    static final int WHOLE_WORD_MAX = 6;

    private static final int SEPARATOR = 0;
    private static final int LOWER = 1;
    private static final int UPPER = 2;
    private static final int UNCASED = 3;
    private static final int DIGIT = 4;

    private TermMatcher() {
    }

    /** The term or text as the guardrail compares it: {@code "Iron-Man_2.stl"} → {@code "ironman2stl"}; empty for null. */
    static String normalise(String text) {
        return text(text).normalised();
    }

    /** Whether a normalised term only matches as a whole word ({@value #WHOLE_WORD_MAX} letters and digits or fewer). */
    static boolean wholeWord(String normalisedTerm) {
        return normalisedTerm != null && normalisedTerm.codePointCount(0, normalisedTerm.length()) <= WHOLE_WORD_MAX;
    }

    /** A text ready to be searched for terms: its normalised form and where its words start and end. */
    static Text text(String raw) {
        BitSet boundaries = new BitSet();
        boundaries.set(0);
        if (raw == null || raw.isEmpty()) {
            return new Text("", boundaries);
        }
        int[] points = Normalizer.normalize(raw, Normalizer.Form.NFKD).codePoints().toArray();
        StringBuilder out = new StringBuilder(points.length);
        int previous = SEPARATOR;
        for (int i = 0; i < points.length; i++) {
            int cp = points[i];
            if (Character.isLetterOrDigit(cp)) {
                int kind = kind(cp);
                if (previous == SEPARATOR
                        || (kind == DIGIT) != (previous == DIGIT)
                        || (previous == LOWER && kind == UPPER)
                        || (previous == UPPER && kind == UPPER && nextLetterIsLower(points, i))) {
                    boundaries.set(out.length());
                }
                out.appendCodePoint(Character.toLowerCase(cp));
                previous = kind;
            } else if (!isMark(cp)) {
                previous = SEPARATOR;
            }
        }
        boundaries.set(out.length());
        return new Text(out.toString(), boundaries);
    }

    /** @param boundaries positions in {@code normalised} (char indices) where a word of the text starts or ends */
    record Text(String normalised, BitSet boundaries) {

        boolean isEmpty() {
            return normalised.isEmpty();
        }

        /** Whether the text mentions a term given in its normalised form (see the class comment for the rule). */
        boolean mentions(String normalisedTerm) {
            if (normalisedTerm == null || normalisedTerm.isEmpty()) {
                return false;
            }
            boolean whole = wholeWord(normalisedTerm);
            for (int at = normalised.indexOf(normalisedTerm); at >= 0; at = normalised.indexOf(normalisedTerm, at + 1)) {
                if (!whole || (boundaries.get(at) && boundaries.get(at + normalisedTerm.length()))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static int kind(int cp) {
        if (Character.isDigit(cp)) {
            return DIGIT;
        }
        if (Character.isUpperCase(cp) || Character.isTitleCase(cp)) {
            return UPPER;
        }
        return Character.isLowerCase(cp) ? LOWER : UNCASED;
    }

    /** The next letter after {@code i}, skipping marks, is lowercase: the capital at {@code i} starts a new word ("DC|Comics"). */
    private static boolean nextLetterIsLower(int[] points, int i) {
        for (int j = i + 1; j < points.length; j++) {
            if (isMark(points[j])) {
                continue;
            }
            return Character.isLetter(points[j]) && Character.isLowerCase(points[j]);
        }
        return false;
    }

    /** Combining marks (vowel signs, viramas, accents) and format characters (zero-width joiners): part of the word they sit in. */
    private static boolean isMark(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT;
    }
}
