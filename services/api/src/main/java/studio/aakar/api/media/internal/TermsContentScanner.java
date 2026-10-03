package studio.aakar.api.media.internal;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import studio.aakar.api.media.ContentScanner;
import studio.aakar.api.media.ContentTermDto;
import studio.aakar.api.media.ScanResult;
import studio.aakar.api.media.UploadKind;

/**
 * The default {@link ContentScanner} ({@code aakar.uploads.scanner=terms}): the trademark guardrail for uploads (plan §8,
 * Katha). A file whose original name mentions an active content term (the portal's Content rules, {@code content_terms}) or one
 * of {@code aakar.uploads.flag-terms} is held for a content review; the reason names the term. Matching follows
 * {@link TermMatcher}: case, spaces and punctuation never count, so {@code "iron man"} catches {@code Marvel_IronMan-v2.stl},
 * and short terms must stand as words. The portal's terms come first (most specific first), then the configured ones in
 * their order. It never looks inside the file.
 */
@Component
@ConditionalOnProperty(name = "aakar.uploads.scanner", havingValue = "terms", matchIfMissing = true)
class TermsContentScanner implements ContentScanner {

    private static final Logger log = LoggerFactory.getLogger(TermsContentScanner.class);

    private final ActiveTerms contentTerms;
    private final List<String> configured;
    private final List<String> configuredNormalised;

    TermsContentScanner(UploadProperties properties, ActiveTerms contentTerms) {
        this.contentTerms = contentTerms;
        this.configured = properties.flagTerms();
        this.configuredNormalised = configured.stream().map(TermMatcher::normalise).toList();
        log.info("Content scanner: file names are checked against the active content terms (portal Content rules) and {} configured flag term(s)",
                configured.size());
    }

    @Override
    public ScanResult scan(UploadKind kind, String originalFilename, String format, byte[] bytes) {
        TermMatcher.Text name = TermMatcher.text(originalFilename);
        if (name.isEmpty()) {
            return ScanResult.clean();
        }
        for (ContentTermDto term : contentTerms.active()) {
            if (name.mentions(term.normalisedTerm())) {
                return ScanResult.flagged(reason(term));
            }
        }
        for (int i = 0; i < configured.size(); i++) {
            if (name.mentions(configuredNormalised.get(i))) {
                return ScanResult.flagged("File name mentions \"" + configured.get(i) + "\", a flagged term");
            }
        }
        return ScanResult.clean();
    }

    /** {@code File name mentions "Iron Man", a protected character · Marvel character (Disney)}: what the reviewer reads. */
    static String reason(ContentTermDto term) {
        String why = term.reason() == null || term.reason().isBlank() ? "" : " · " + term.reason().trim();
        return "File name mentions \"" + term.term() + "\", a protected " + term.kind().noun() + why;
    }
}
