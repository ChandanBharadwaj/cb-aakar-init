package studio.aakar.api.media.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import studio.aakar.api.media.ScanResult;
import studio.aakar.api.media.UploadKind;

/** The seed of the trademark guardrail: file names that mention a flagged term go to a reviewer. */
class TermsContentScannerTest {

    private final TermsContentScanner scanner = new TermsContentScanner(
            new UploadProperties("terms", List.of("Marvel", "iron man", " ", "बैटमैन"), DataSize.ofMegabytes(15), DataSize.ofMegabytes(50)));

    @Test
    void flagsNamesThatMentionATermIgnoringCaseSpacesAndPunctuation() {
        assertThat(scanner.scan(UploadKind.model, "marvel_ironman.stl", "stl", new byte[0])).satisfies(r -> {
            assertThat(r.verdict()).isEqualTo(ScanResult.Verdict.flagged);
            assertThat(r.reason()).isEqualTo("File name mentions \"Marvel\", a flagged term");
        });
        assertThat(scanner.scan(UploadKind.model, "My-Iron_Man v2.3mf", "3mf", new byte[0]).isFlagged()).isTrue();
        assertThat(scanner.scan(UploadKind.image, "बैटमैन.png", "png", new byte[0]).isFlagged()).isTrue();
    }

    @Test
    void everythingElseIsClean() {
        assertThat(scanner.scan(UploadKind.image, "asha-birthday.jpg", "jpg", new byte[0])).isEqualTo(ScanResult.clean());
        assertThat(scanner.scan(UploadKind.image, null, "jpg", new byte[0])).isEqualTo(ScanResult.clean());
        TermsContentScanner none = new TermsContentScanner(new UploadProperties("terms", null, DataSize.ofMegabytes(15), DataSize.ofMegabytes(50)));
        assertThat(none.scan(UploadKind.model, "marvel.stl", "stl", new byte[0]).isFlagged()).isFalse();
        assertThat(new NoopContentScanner().scan(UploadKind.model, "marvel.stl", "stl", new byte[0])).isEqualTo(ScanResult.clean());
    }

    @Test
    void compactKeepsLettersAndDigitsOnly() {
        assertThat(TermsContentScanner.compact("Iron-Man_2.stl")).isEqualTo("ironman2stl");
        assertThat(TermsContentScanner.compact(null)).isEmpty();
    }
}
