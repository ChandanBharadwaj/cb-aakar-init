package studio.aakar.api.templates.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import studio.aakar.api.support.Contracts;
import studio.aakar.api.templates.MotifDto;

/**
 * The motif library loader: the monorepo's library read in index order, the default folder found from the working
 * directory, file names that cannot leave the folder, the geometry service's entry rules, and a missing or broken library
 * that is only a warning locally but stops a production start.
 */
class MotifLibraryTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><path d=\"M10 10 H90 V90 H10 Z\"/></svg>";
    static final String PUBLIC_URL = "https://api.aakar.example";

    @TempDir
    Path dir;

    @Test
    void theMonoreposLibraryIsReadInIndexOrder() throws IOException {
        Path index = Contracts.require(Contracts.designTokens("motifs/index.json"));
        MotifLibrary library = new MotifLibrary(index.getParent(), PUBLIC_URL, true);

        assertThat(library.available()).isTrue();
        JsonNode entries = Contracts.readJson(index).get("motifs");
        assertThat(library.list()).hasSize(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            JsonNode entry = entries.get(i);
            MotifDto motif = library.list().get(i);
            String id = entry.get("id").asText();
            assertThat(motif.id()).isEqualTo(id);
            assertThat(motif.label()).isEqualTo(entry.get("label").asText());
            assertThat(motif.minScale()).isEqualTo(entry.get("min_scale").asDouble());
            List<String> tags = new ArrayList<>();
            entry.get("tags").forEach(t -> tags.add(t.asText()));
            assertThat(motif.tags()).isEqualTo(tags);
            assertThat(motif.svgUrl()).isEqualTo(PUBLIC_URL + "/api/motifs/" + id + ".svg");
            assertThat(library.find(id)).contains(motif);
            assertThat(library.svg(id)).hasValueSatisfying(bytes -> {
                try {
                    assertThat(bytes).isEqualTo(Files.readAllBytes(index.resolveSibling(entry.get("file").asText())));
                } catch (IOException e) {
                    throw new AssertionError(e);
                }
            });
        }
        assertThat(library.find("peacock")).isEmpty();
        assertThat(library.find(null)).isEmpty();
        assertThat(library.svg("peacock")).isEmpty();
        assertThat(library.svg("../index")).isEmpty();
        // callers get a copy of the artwork
        library.svg("lotus").orElseThrow()[0] = 'X';
        assertThat(library.svg("lotus").orElseThrow()[0]).isNotEqualTo((byte) 'X');
    }

    @Test
    void withoutASettingTheLibraryIsTheMonoreposFolder() {
        Contracts.require(Contracts.designTokens("motifs/index.json"));
        for (String blank : new String[] {null, "", "  "}) {
            Path found = MotifLibrary.locate(blank);
            assertThat(found).isNotNull();
            assertThat(found.endsWith(Paths.get("packages", "design-tokens", "motifs"))).as(String.valueOf(found)).isTrue();
            assertThat(found.resolve("index.json")).isRegularFile();
        }
        assertThat(MotifLibrary.locate(" /design-tokens/motifs ")).isEqualTo(Paths.get("/design-tokens/motifs"));
    }

    @Test
    void entriesFollowTheGeometryServicesRules() throws IOException {
        Files.writeString(dir.resolve("lotus.svg"), SVG);
        Files.writeString(dir.resolve("star_rangoli.svg"), SVG);
        // defaults: the file is <id>.svg, the label comes from the id, min_scale is the contract's 0.2, no tags
        write(List.of(Map.of("id", "star_rangoli")));
        MotifDto star = MotifLibrary.load(dir, PUBLIC_URL).motifs().get(0);
        assertThat(star).isEqualTo(new MotifDto("star_rangoli", "Star rangoli", List.of(), 0.2, PUBLIC_URL + "/api/motifs/star_rangoli.svg"));

        assertBroken(List.of(Map.of("id", "lotus"), Map.of("id", "lotus")), "malformed or repeated");
        assertBroken(List.of(Map.of("id", "Lotus")), "malformed or repeated");
        assertBroken(List.of(Map.of("id", "lotus", "min_scale", 0.1)), "min_scale of lotus must be a number between 0.2 and 1");
        assertBroken(List.of(Map.of("id", "lotus", "min_scale", 1.5)), "between 0.2 and 1");
        assertBroken(List.of(Map.of("id", "lotus", "min_scale", "0.3")), "between 0.2 and 1");
        assertBroken(List.of(Map.of("id", "paisley")), "missing or outside the library folder");
        assertBroken(List.of(), "lists no motifs");
        Files.writeString(dir.resolve("note.svg"), "just text");
        assertBroken(List.of(Map.of("id", "note")), "is not an SVG document");
    }

    @Test
    void aFileNameCannotLeaveTheLibraryFolder() throws IOException {
        Path library = Files.createDirectory(dir.resolve("motifs"));
        Files.writeString(dir.resolve("secret.svg"), SVG);
        Files.createDirectory(library.resolve("sub"));
        Files.writeString(library.resolve("sub/lotus.svg"), SVG);
        for (String file : List.of("../secret.svg", "/etc/passwd.svg", "sub/lotus.svg", "sub\\lotus.svg", "..svg", ".hidden.svg", "lotus.png",
                "lotus.svg\u0000.png", "%2e%2e%2fsecret.svg")) {
            Files.writeString(library.resolve("index.json"), JSON.writeValueAsString(Map.of("motifs", List.of(Map.of("id", "lotus", "file", file)))));
            assertThatThrownBy(() -> MotifLibrary.load(library, PUBLIC_URL)).as(file).isInstanceOf(MotifLibrary.LibraryException.class)
                    .hasMessageContaining("lotus");
        }
        // nor through a symbolic link
        Files.createSymbolicLink(library.resolve("lotus.svg"), dir.resolve("secret.svg"));
        Files.writeString(library.resolve("index.json"), JSON.writeValueAsString(Map.of("motifs", List.of(Map.of("id", "lotus")))));
        assertThatThrownBy(() -> MotifLibrary.load(library, PUBLIC_URL)).isInstanceOf(MotifLibrary.LibraryException.class)
                .hasMessageContaining("outside the library folder");
    }

    @Test
    void aMissingOrBrokenLibraryIsAWarningLocallyAndStopsAProductionStart() throws IOException {
        for (Path missing : new Path[] {null, dir.resolve("nowhere")}) {
            MotifLibrary local = new MotifLibrary(missing, PUBLIC_URL, false);
            assertThat(local.available()).isFalse();
            assertThat(local.list()).isEmpty();
            assertThat(local.find("lotus")).isEmpty();
            assertThat(local.svg("lotus")).isEmpty();
            assertThatThrownBy(() -> new MotifLibrary(missing, PUBLIC_URL, true)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("aakar.profile=production needs the motif library (aakar.motifs.dir, AAKAR_MOTIFS_DIR)");
        }
        Files.writeString(dir.resolve("index.json"), "{\"motifs\": [");
        assertThat(new MotifLibrary(dir, PUBLIC_URL, false).available()).isFalse();
        assertThatThrownBy(() -> new MotifLibrary(dir, PUBLIC_URL, true)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("index.json is not JSON");
        Files.delete(dir.resolve("index.json"));
        assertThatThrownBy(() -> new MotifLibrary(dir, PUBLIC_URL, true)).hasMessageContaining("index.json is missing");
    }

    private void write(List<Map<String, Object>> motifs) throws IOException {
        Map<String, Object> index = new LinkedHashMap<>();
        index.put("reference_mm", 30);
        index.put("motifs", motifs);
        Files.writeString(dir.resolve("index.json"), JSON.writeValueAsString(index), StandardCharsets.UTF_8);
    }

    private void assertBroken(List<Map<String, Object>> motifs, String reason) throws IOException {
        write(motifs);
        assertThatThrownBy(() -> MotifLibrary.load(dir, PUBLIC_URL)).isInstanceOf(MotifLibrary.LibraryException.class).hasMessageContaining(reason);
        assertThat(new MotifLibrary(dir, PUBLIC_URL, false).available()).isFalse();
    }
}
