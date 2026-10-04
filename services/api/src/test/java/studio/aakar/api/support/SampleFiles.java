package studio.aakar.api.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Small but genuine customer files for upload tests: each starts with the bytes its format is recognised by. */
public final class SampleFiles {

    private SampleFiles() {
    }

    public static byte[] png() {
        return image("png");
    }

    public static byte[] jpeg() {
        return image("jpg");
    }

    /** A RIFF/WEBP header (the sniffer reads the signature only). */
    public static byte[] webp() {
        ByteBuffer b = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(22).put("WEBPVP8L".getBytes(StandardCharsets.US_ASCII));
        return b.array();
    }

    /** An ISO-BMFF {@code ftyp} box with the {@code heic} brand. */
    public static byte[] heic() {
        ByteBuffer b = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN);
        b.putInt(24).put("ftypheic".getBytes(StandardCharsets.US_ASCII)).putInt(0).put("mif1heic".getBytes(StandardCharsets.US_ASCII));
        return b.array();
    }

    /** A binary STL: 80-byte header, triangle count, 50 bytes per triangle (one right triangle). */
    public static byte[] binaryStl() {
        ByteBuffer b = ByteBuffer.allocate(84 + 50).order(ByteOrder.LITTLE_ENDIAN);
        byte[] header = new byte[80];
        byte[] name = "solid binary-but-says-solid".getBytes(StandardCharsets.US_ASCII); // binary STLs may start with "solid" too
        System.arraycopy(name, 0, header, 0, name.length);
        b.put(header).putInt(1);
        float[] values = {0, 0, 1, 0, 0, 0, 10, 0, 0, 0, 10, 0};
        for (float v : values) {
            b.putFloat(v);
        }
        b.putShort((short) 0);
        return b.array();
    }

    public static byte[] asciiStl() {
        return """
                solid tile
                  facet normal 0 0 1
                    outer loop
                      vertex 0 0 0
                      vertex 10 0 0
                      vertex 0 10 0
                    endloop
                  endfacet
                endsolid tile
                """.getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] obj() {
        return """
                # a single triangle
                o tile
                v 0 0 0
                v 10 0 0
                v 0 10 0
                f 1 2 3
                """.getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] ply() {
        return """
                ply
                format ascii 1.0
                element vertex 3
                property float x
                property float y
                property float z
                element face 1
                property list uchar int vertex_indices
                end_header
                0 0 0
                10 0 0
                0 10 0
                3 0 1 2
                """.getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] off() {
        return """
                OFF
                3 1 0
                0 0 0
                10 0 0
                0 10 0
                3 0 1 2
                """.getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] gltf() {
        return """
                {"asset": {"version": "2.0"}, "scenes": [{"nodes": []}], "scene": 0}
                """.getBytes(StandardCharsets.UTF_8);
    }

    /** A GLB container header ({@code glTF}, version 2, total length) with an empty JSON chunk. */
    public static byte[] glb() {
        byte[] json = "{\"asset\":{\"version\":\"2.0\"}}  ".getBytes(StandardCharsets.UTF_8); // padded to 4 bytes
        ByteBuffer b = ByteBuffer.allocate(12 + 8 + json.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("glTF".getBytes(StandardCharsets.US_ASCII)).putInt(2).putInt(12 + 8 + json.length);
        b.putInt(json.length).put("JSON".getBytes(StandardCharsets.US_ASCII)).put(json);
        return b.array();
    }

    /** A 3MF package: a zip with the content types and the model part. */
    public static byte[] threeMf() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("3D/3dmodel.model"));
            zip.write(GeometryStub.STUB_3MF.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** {@code size} bytes that start like a PNG (for size limits: only the signature is checked). */
    public static byte[] pngOfSize(int size) {
        byte[] bytes = new byte[size];
        byte[] png = png();
        System.arraycopy(png, 0, bytes, 0, Math.min(png.length, size));
        return bytes;
    }

    private static byte[] image(String format) {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                image.setRGB(x, y, (x + y) % 2 == 0 ? 0xC0603A : 0x1E2A5A);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No ImageIO writer for " + format);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
