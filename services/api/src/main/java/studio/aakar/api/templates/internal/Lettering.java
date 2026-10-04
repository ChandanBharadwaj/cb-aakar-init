package studio.aakar.api.templates.internal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import studio.aakar.api.shared.ProblemCodes;

/**
 * The rules for the letters of a text (Naam) that need no font file, as the geometry service's
 * {@code features/emboss_text.py} ({@code resolve_script}) applies them before shaping: one line; letters of the seven launch
 * scripts only (Latin and six Indian scripts, told apart by Unicode block; digits, spaces and punctuation belong to none);
 * one script per text; a chosen {@code script} that matches the letters; and a {@code font}, when given, that names the
 * bundled lettering: Noto Sans or the script's own Noto Sans, bold or not, ignoring case, spaces and punctuation (the
 * table in {@code services/geometry/aakar_geometry/fonts/README.md}). Whether every character has a glyph in those fonts
 * and whether the letters fit their spot at a printable stroke depend on the font files and the anchor's size; the
 * geometry service checks those before it cuts anything.
 */
final class Lettering {

    static final String LATIN = "latin";

    /** A launch script: its contract id, its customer name, the family of its bundled font and its Unicode blocks. */
    record Script(String id, String label, String family, int[] ranges) {

        boolean owns(int codePoint) {
            for (int i = 0; i < ranges.length; i += 2) {
                if (codePoint >= ranges[i] && codePoint <= ranges[i + 1]) {
                    return true;
                }
            }
            return false;
        }
    }

    /** What is wrong with a text: the problem code, the setting at fault ({@code text}, {@code script}, {@code font}), the sentence. */
    record Refusal(String code, String field, String detail) {
    }

    /** {@code emboss_text.SCRIPTS} in the same order; the dandas U+0964/U+0965 are shared by every Indian script, so punctuation. */
    static final Map<String, Script> SCRIPTS = scripts(
            new Script(LATIN, "Latin", "Noto Sans",
                    new int[] {0x41, 0x5A, 0x61, 0x7A, 0xAA, 0xAA, 0xBA, 0xBA, 0xC0, 0xD6, 0xD8, 0xF6, 0xF8, 0x24F, 0x1E00, 0x1EFF}),
            new Script("devanagari", "Devanagari", "Noto Sans Devanagari", new int[] {0x900, 0x963, 0x966, 0x97F, 0xA8E0, 0xA8FF, 0x1CD0, 0x1CFF}),
            new Script("telugu", "Telugu", "Noto Sans Telugu", new int[] {0xC00, 0xC7F}),
            new Script("tamil", "Tamil", "Noto Sans Tamil", new int[] {0xB80, 0xBFF, 0x11FC0, 0x11FFF}),
            new Script("kannada", "Kannada", "Noto Sans Kannada", new int[] {0xC80, 0xCFF}),
            new Script("bengali", "Bengali", "Noto Sans Bengali", new int[] {0x980, 0x9FF}),
            new Script("gujarati", "Gujarati", "Noto Sans Gujarati", new int[] {0xA80, 0xAFF}));

    private Lettering() {
    }

    /** The first refusal for this text with its {@code script} and {@code font} (either may be null), or null when it can be lettered. */
    static Refusal check(String text, String script, String font) {
        String value = normalise(text);
        if (value.isEmpty()) {
            return new Refusal(ProblemCodes.VALIDATION_FAILED, "text", "Type the text (Naam) to add");
        }
        if (value.codePoints().anyMatch(cp -> Character.getType(cp) == Character.CONTROL)) {
            return new Refusal(ProblemCodes.VALIDATION_FAILED, "text", "Text (Naam) goes on a single line");
        }
        List<String> scripts = new ArrayList<>();
        List<Integer> foreign = new ArrayList<>();
        value.codePoints().forEach(cp -> {
            Script owner = scriptOf(cp);
            if (owner != null) {
                if (!scripts.contains(owner.id())) {
                    scripts.add(owner.id());
                }
            } else if (Character.isLetter(cp) && !foreign.contains(cp)) {
                foreign.add(cp);
            }
        });
        if (!foreign.isEmpty()) {
            String letters = foreign.stream().limit(3).map(Character::toString).collect(Collectors.joining());
            return new Refusal(ProblemCodes.VALIDATION_FAILED, "text", "We can print Latin letters and six Indian scripts (Devanagari, Telugu, "
                    + "Tamil, Kannada, Bengali, Gujarati); “" + letters + "” is not one of them yet");
        }
        if (scripts.size() > 1) {
            List<String> names = scripts.stream().map(s -> SCRIPTS.get(s).label()).toList();
            String mix = String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
            return new Refusal(ProblemCodes.VALIDATION_FAILED, "text", "Please write the text (Naam) in one script: this mixes " + mix
                    + " letters. Each can go on its own spot");
        }
        Script detected = scripts.isEmpty() ? null : SCRIPTS.get(scripts.get(0));
        Script chosen;
        if (script != null) {
            chosen = SCRIPTS.get(script);
            if (chosen == null) {
                return new Refusal(ProblemCodes.VALIDATION_FAILED, "script", "The script of the text (Naam) must be one of "
                        + String.join(", ", SCRIPTS.keySet()));
            }
            if (detected != null && !detected.id().equals(chosen.id())) {
                return new Refusal(ProblemCodes.VALIDATION_FAILED, "script", "This text (Naam) is written in " + detected.label() + " letters, but "
                        + chosen.label() + " lettering was chosen; choose " + detected.label() + " or let us pick");
            }
        } else {
            chosen = detected == null ? SCRIPTS.get(LATIN) : detected;
        }
        if (font != null && !normalise(font).isEmpty() && !bundled(font, chosen)) {
            return new Refusal(ProblemCodes.UNSUPPORTED_FEATURE, "font", "The lettering style “" + font + "” is not available yet; leave the font "
                    + "empty for our standard " + chosen.label() + " lettering");
        }
        return null;
    }

    /** The launch script a letter or sign belongs to, or null (digits, spaces, punctuation, symbols, other scripts). */
    static Script scriptOf(int codePoint) {
        for (Script script : SCRIPTS.values()) {
            if (script.owns(codePoint)) {
                return script;
            }
        }
        return null;
    }

    /** NFC with the surrounding spaces trimmed, as the geometry service (and the storefront) trim them. */
    static String normalise(String text) {
        String value = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC);
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isSpace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    /** Python's {@code str.isspace}: Java whitespace, the no-break spaces and NEL. */
    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85;
    }

    /** "Noto Sans", "noto_sans", "NotoSans-Bold" or the script's own family ("Noto Sans Devanagari"), bold or not. */
    private static boolean bundled(String font, Script script) {
        String own = letters(script.family()); // "notosans" again for Latin, so a list rather than a set
        return List.of("notosans", "notosansbold", own, own + "bold").contains(letters(font));
    }

    private static String letters(String name) {
        StringBuilder out = new StringBuilder();
        name.toLowerCase(Locale.ROOT).codePoints().filter(Character::isLetterOrDigit).forEach(out::appendCodePoint);
        return out.toString();
    }

    private static Map<String, Script> scripts(Script... scripts) {
        Map<String, Script> map = new LinkedHashMap<>();
        for (Script script : scripts) {
            map.put(script.id(), script);
        }
        return Collections.unmodifiableMap(map);
    }
}
