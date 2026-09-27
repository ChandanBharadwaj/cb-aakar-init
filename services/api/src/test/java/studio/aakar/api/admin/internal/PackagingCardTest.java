package studio.aakar.api.admin.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

/** The packaging card is a real one-page PDF. */
class PackagingCardTest {

    @Test
    void rendersAPdf() {
        byte[] pdf = PackagingCard.render(new PackagingCard.Content("AK-000007", "Jharokha Phone Stand", "Terracotta Silk",
                "Printed in Bengaluru · 27 September 2026", "http://localhost:3000/k/ABCD2345"));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1_000);
        String tail = new String(pdf, Math.max(0, pdf.length - 64), Math.min(64, pdf.length), StandardCharsets.US_ASCII);
        assertThat(tail).contains("%%EOF");
    }

    @Test
    void charactersOutsideHelveticaAreReplacedNotFatal() {
        PDType1Font helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        assertThat(PackagingCard.printable(helvetica, "Order AK-1 · ₹1,249 ✓")).isEqualTo("Order AK-1 · ?1,249 ?");
        byte[] pdf = PackagingCard.render(new PackagingCard.Content("AK-000009", "Nameplate · ₹ sign in title", "Polished Brass",
                "Printed in Bengaluru · 1 October 2026", "http://localhost:3000/k/ZZZZ7777"));
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
