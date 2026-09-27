package studio.aakar.api.media.internal;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.aakar.api.media.ContentScanner;
import studio.aakar.api.media.ScanResult;
import studio.aakar.api.media.UploadKind;

/**
 * The default {@link ContentScanner} ({@code aakar.uploads.scanner=terms}): flags an upload whose original file name
 * mentions one of {@code aakar.uploads.flag-terms}, comparing letters and digits only and ignoring case, so
 * {@code "iron man"} catches {@code Marvel_IronMan-v2.stl}. With no terms configured every file is clean. This is the
 * seed of the trademark guardrail (plan §8, Katha); it never looks inside the file.
 */
@Component
@ConditionalOnProperty(name = "aakar.uploads.scanner", havingValue = "terms", matchIfMissing = true)
class TermsContentScanner implements ContentScanner {

    private static final Logger log = LoggerFactory.getLogger(TermsContentScanner.class);

    private final List<String> terms;
    private final List<String> compactTerms;

    TermsContentScanner(UploadProperties properties) {
        this.terms = properties.flagTerms();
        this.compactTerms = terms.stream().map(TermsContentScanner::compact).toList();
        log.info("Content scanner: file names are checked against {} flagged term(s)", terms.size());
    }

    @Override
    public ScanResult scan(UploadKind kind, String originalFilename, String format, byte[] bytes) {
        String name = compact(originalFilename);
        if (name.isEmpty()) {
            return ScanResult.clean();
        }
        for (int i = 0; i < terms.size(); i++) {
            String term = compactTerms.get(i);
            if (!term.isEmpty() && name.contains(term)) {
                return ScanResult.flagged("File name mentions \"" + terms.get(i) + "\", a flagged term");
            }
        }
        return ScanResult.clean();
    }

    /** Lowercase letters and digits only: {@code "Iron-Man_2.stl"} → {@code "ironman2stl"}. */
    static String compact(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        text.toLowerCase(Locale.ROOT).codePoints().filter(Character::isLetterOrDigit).forEach(out::appendCodePoint);
        return out.toString();
    }
}
