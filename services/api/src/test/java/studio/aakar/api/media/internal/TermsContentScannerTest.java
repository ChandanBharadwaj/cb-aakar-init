package studio.aakar.api.media.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import studio.aakar.api.media.ContentTermDto;
import studio.aakar.api.media.ContentTermKind;
import studio.aakar.api.media.ScanResult;
import studio.aakar.api.media.UploadKind;

/**
 * The trademark guardrail for uploads: file names that mention an active content term (the portal's list, most specific first)
 * or a configured flag term go to a reviewer, with the term in the reason.
 */
class TermsContentScannerTest {

    static final ActiveTerms NONE = List::of;

    private final TermsContentScanner configuredOnly = new TermsContentScanner(properties(List.of("Marvel", "iron man", " ", "बैटमैन")), NONE);

    @Test
    void flagsNamesThatMentionATermIgnoringCaseSpacesAndPunctuation() {
        assertThat(configuredOnly.scan(UploadKind.model, "marvel_ironman.stl", "stl", new byte[0])).satisfies(r -> {
            assertThat(r.verdict()).isEqualTo(ScanResult.Verdict.flagged);
            assertThat(r.reason()).isEqualTo("File name mentions \"Marvel\", a flagged term");
        });
        assertThat(configuredOnly.scan(UploadKind.model, "My-Iron_Man v2.3mf", "3mf", new byte[0]).isFlagged()).isTrue();
        assertThat(configuredOnly.scan(UploadKind.image, "बैटमैन.png", "png", new byte[0]).isFlagged()).isTrue();
        // a short configured term stands as a word, like the portal's
        assertThat(configuredOnly.scan(UploadKind.image, "marvellous-mum.png", "png", new byte[0]).isFlagged()).isFalse();
    }

    @Test
    void thePortalsTermsComeFirstMostSpecificFirstWithTheirKindAndReason() {
        ContentTermDto ironMan = term("Iron Man", ContentTermKind.character, "Marvel character (Disney)");
        ContentTermDto marvel = term("Marvel", ContentTermKind.trademark, "Marvel Comics brand (Disney)");
        ContentTermDto bare = term("Kaptaan Zorbo", ContentTermKind.other, null);
        TermsContentScanner scanner = new TermsContentScanner(properties(List.of("marvel", "pikachu")), () -> List.of(ironMan, marvel, bare));

        assertThat(scanner.scan(UploadKind.image, "Marvel_IronMan-poster.png", "png", new byte[0]).reason())
                .isEqualTo("File name mentions \"Iron Man\", a protected character · Marvel character (Disney)");
        assertThat(scanner.scan(UploadKind.model, "marvel-logo.stl", "stl", new byte[0]).reason())
                .isEqualTo("File name mentions \"Marvel\", a protected trademark · Marvel Comics brand (Disney)");
        assertThat(scanner.scan(UploadKind.model, "kaptaan_zorbo.obj", "obj", new byte[0]).reason())
                .isEqualTo("File name mentions \"Kaptaan Zorbo\", a protected term");
        // the configured terms still apply on top of the portal's
        assertThat(scanner.scan(UploadKind.image, "Pikachu.png", "png", new byte[0]).reason()).isEqualTo("File name mentions \"pikachu\", a flagged term");
        assertThat(scanner.scan(UploadKind.image, "asha-birthday.jpg", "jpg", new byte[0])).isEqualTo(ScanResult.clean());
    }

    @Test
    void everythingElseIsClean() {
        assertThat(configuredOnly.scan(UploadKind.image, "asha-birthday.jpg", "jpg", new byte[0])).isEqualTo(ScanResult.clean());
        assertThat(configuredOnly.scan(UploadKind.image, null, "jpg", new byte[0])).isEqualTo(ScanResult.clean());
        TermsContentScanner none = new TermsContentScanner(new UploadProperties("terms", null, DataSize.ofMegabytes(15), DataSize.ofMegabytes(50)), NONE);
        assertThat(none.scan(UploadKind.model, "marvel.stl", "stl", new byte[0]).isFlagged()).isFalse();
        assertThat(new NoopContentScanner().scan(UploadKind.model, "marvel.stl", "stl", new byte[0])).isEqualTo(ScanResult.clean());
    }

    @Test
    void theReasonNamesTheTermItsKindAndWhy() {
        assertThat(TermsContentScanner.reason(term("Batman", ContentTermKind.character, "  DC character  ")))
                .isEqualTo("File name mentions \"Batman\", a protected character · DC character");
        assertThat(TermsContentScanner.reason(term("DC", ContentTermKind.trademark, " "))).isEqualTo("File name mentions \"DC\", a protected trademark");
    }

    private static UploadProperties properties(List<String> flagTerms) {
        return new UploadProperties("terms", flagTerms, DataSize.ofMegabytes(15), DataSize.ofMegabytes(50));
    }

    private static ContentTermDto term(String term, ContentTermKind kind, String reason) {
        String normalised = TermMatcher.normalise(term);
        return new ContentTermDto(UUID.randomUUID(), term, kind, reason, true, normalised, TermMatcher.wholeWord(normalised), Instant.EPOCH, Instant.EPOCH);
    }
}
