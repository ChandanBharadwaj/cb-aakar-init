package studio.aakar.api.media.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import studio.aakar.api.media.UploadKind;
import studio.aakar.api.support.SampleFiles;

/** Formats are recognised by extension, then by their first bytes, so a renamed or damaged file is refused before storage. */
class UploadFormatsTest {

    @Test
    void extensionsMapToTheContractFormats() {
        assertThat(UploadFormats.byFilename("photo.PNG")).hasValueSatisfying(f -> assertThat(f.id()).isEqualTo("png"));
        assertThat(UploadFormats.byFilename("photo.jpeg")).hasValueSatisfying(f -> assertThat(f.id()).isEqualTo("jpg"));
        assertThat(UploadFormats.byFilename("dragon_v2.stl")).hasValueSatisfying(f -> {
            assertThat(f.id()).isEqualTo("stl");
            assertThat(f.kind()).isEqualTo(UploadKind.model);
        });
        assertThat(UploadFormats.byFilename("part.3mf")).hasValueSatisfying(f -> assertThat(f.contentType()).isEqualTo("model/3mf"));
        assertThat(UploadFormats.byFilename("notes.txt")).isEmpty();
        assertThat(UploadFormats.byFilename("no-extension")).isEmpty();
        assertThat(UploadFormats.byFilename("trailing.")).isEmpty();
        assertThat(UploadFormats.byFilename(null)).isEmpty();
        assertThat(UploadFormats.ids(UploadKind.image)).containsExactly("png", "jpg", "webp", "heic");
        assertThat(UploadFormats.ids(UploadKind.model)).containsExactly("stl", "glb", "3mf", "obj", "ply", "off", "gltf");
    }

    @Test
    void everyGenuineSampleMatchesItsOwnSignatureOnly() {
        Map<String, byte[]> samples = Map.ofEntries(
                Map.entry("png", SampleFiles.png()), Map.entry("jpg", SampleFiles.jpeg()), Map.entry("webp", SampleFiles.webp()),
                Map.entry("heic", SampleFiles.heic()), Map.entry("stl", SampleFiles.binaryStl()), Map.entry("glb", SampleFiles.glb()),
                Map.entry("3mf", SampleFiles.threeMf()), Map.entry("obj", SampleFiles.obj()), Map.entry("ply", SampleFiles.ply()),
                Map.entry("off", SampleFiles.off()), Map.entry("gltf", SampleFiles.gltf()));
        samples.forEach((id, bytes) -> UploadFormats.FORMATS.values().forEach(format -> assertThat(format.matches(bytes))
                .as("%s sample against the %s signature", id, format.id()).isEqualTo(format.id().equals(id))));
        assertThat(UploadFormats.FORMATS.get("stl").matches(SampleFiles.asciiStl())).isTrue();
    }

    @Test
    void mismatchedOrDamagedFilesAreRefused() {
        UploadFormats.Format png = UploadFormats.FORMATS.get("png");
        UploadFormats.Format stl = UploadFormats.FORMATS.get("stl");
        assertThat(png.matches("just some text".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(png.matches(new byte[0])).isFalse();
        assertThat(stl.matches(SampleFiles.png())).isFalse();
        // a binary STL whose triangle count disagrees with its length is damaged
        byte[] truncated = java.util.Arrays.copyOf(SampleFiles.binaryStl(), 120);
        assertThat(stl.matches(truncated)).isFalse();
        // "solid" alone is not an ASCII STL
        assertThat(stl.matches("solid but nothing else".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(UploadFormats.FORMATS.get("obj").matches("# only a comment\n".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(UploadFormats.FORMATS.get("gltf").matches("{\"scenes\": []}".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(UploadFormats.FORMATS.get("off").matches("# header\n\nCOFF\n3 1 0\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
    }
}
