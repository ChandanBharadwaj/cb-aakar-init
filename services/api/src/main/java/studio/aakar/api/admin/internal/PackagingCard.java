package studio.aakar.api.admin.internal;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * The card that goes in the box: one A6 page with the brand line, the piece, its finish, where and when it was
 * printed, the order number and the reprint / remix link as text and QR code. Pure: bytes in, PDF out.
 */
final class PackagingCard {

    static final String BRAND = "Aakar";
    static final String TAGLINE = "Designed by You. Crafted by Aakar.";
    static final int QR_SIZE = 220;
    private static final float MARGIN = 28f;

    private PackagingCard() {
    }

    /**
     * @param title the piece (first item title plus a count of the others)
     * @param finish material names, comma separated
     * @param printedLine e.g. {@code Printed in Bengaluru · 27 September 2026}
     * @param link the full {@code /k/{code}} URL
     */
    record Content(String orderNumber, String title, String finish, String printedLine, String link) {
    }

    static byte[] render(Content content) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A6);
            document.addPage(page);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float width = page.getMediaBox().getWidth();
            float y = page.getMediaBox().getHeight() - MARGIN - 18;
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                y = line(stream, bold, 20, MARGIN, y, BRAND);
                y = line(stream, regular, 10, MARGIN, y - 2, TAGLINE);
                y -= 14;
                y = line(stream, bold, 13, MARGIN, y, content.title());
                y = line(stream, regular, 10, MARGIN, y, "Finish: " + content.finish());
                y = line(stream, regular, 10, MARGIN, y, content.printedLine());
                y = line(stream, regular, 10, MARGIN, y, "Order " + content.orderNumber());
                y -= 10;
                y = line(stream, bold, 10, MARGIN, y, "Reprint or remix this piece");
                y = line(stream, regular, 9, MARGIN, y, content.link());

                PDImageXObject qr = LosslessFactory.createFromImage(document, qrImage(content.link()));
                float size = Math.min(150f, y - MARGIN - 8);
                stream.drawImage(qr, (width - size) / 2, MARGIN, size, size);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot render the packaging card", e);
        }
    }

    private static float line(PDPageContentStream stream, PDFont font, float size, float x, float y, String text) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(printable(font, text == null ? "" : text));
        stream.endText();
        return y - size - 5;
    }

    /** Replaces characters the standard Helvetica encoding lacks (the rupee sign, emoji) so rendering never fails. */
    static String printable(PDFont font, String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            String s = new String(Character.toChars(cp));
            try {
                font.encode(s);
                out.append(s);
            } catch (IOException | IllegalArgumentException e) {
                out.append('?');
            }
        });
        return out.toString();
    }

    private static BufferedImage qrImage(String link) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE,
                    Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (WriterException e) {
            throw new IllegalStateException("Cannot encode the share link as a QR code", e);
        }
    }
}
