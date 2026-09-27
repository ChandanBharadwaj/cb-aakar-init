package studio.aakar.api;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import studio.aakar.api.admin.StaffAccounts;
import studio.aakar.api.admin.StaffRole;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.Contracts;
import studio.aakar.api.support.SampleFiles;

/**
 * The motif library (Buti) behind the Chhaap panel: {@code GET /api/motifs} lists {@code packages/design-tokens/motifs} in
 * index order with preview URLs, each artwork is served as SVG with cache headers, the portal reads the same rows with any
 * staff role, an unknown motif is 404 {@code unknown_motif} and path tricks never reach a file; and at design time a motif
 * the library lacks, one below its printable scale, or a photo crowded by a motif or a text is refused before any build.
 */
class MotifsIntegrationTest extends AbstractIntegrationTest {

    static final String PUBLIC_URL = "http://localhost:8080"; // aakar.api.public-url in application-test.yml
    static final String CREATE = "{\"source\": \"create\", \"family_id\": \"keychain\", \"features\": [%s]}";
    static final String RELIEF = "{\"type\": \"relief_image\", \"source\": {\"upload_id\": \"%s\"}, \"anchor\": \"%s\"}";

    @Autowired
    StaffAccounts staffAccounts;

    JsonNode index;

    @BeforeEach
    void library() {
        index = Contracts.readJson(Contracts.require(Contracts.designTokens("motifs/index.json")));
    }

    @Test
    void theLibraryIsListedWithPreviewUrlsForTheShopAndThePortal() {
        ResponseEntity<String> response = get("/api/motifs");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        JsonNode motifs = body(response);
        JsonNode entries = index.get("motifs");
        assertThat(motifs).hasSize(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            JsonNode motif = motifs.get(i);
            JsonNode entry = entries.get(i);
            String id = entry.get("id").asText();
            assertThat(motif.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "label", "tags", "min_scale", "svg_url");
            assertThat(motif.get("id").asText()).isEqualTo(id);
            assertThat(motif.get("label").asText()).isEqualTo(entry.get("label").asText());
            assertThat(motif.get("tags")).isEqualTo(entry.get("tags"));
            assertThat(motif.get("min_scale").asDouble()).isEqualTo(entry.get("min_scale").asDouble());
            assertThat(motif.get("svg_url").asText()).isEqualTo(PUBLIC_URL + "/api/motifs/" + id + ".svg");
        }
        assertThat(motifs.findValuesAsText("id")).contains("paisley", "lotus", "star_rangoli", "jaali_lattice", "warli_dancer");

        // the portal reads the same rows with any staff role, and only with one
        assertThat(body(get("/admin/api/motifs", staffOwner()))).isEqualTo(motifs);
        String email = "karigar-buti-" + UUID.randomUUID() + "@aakar.local";
        staffAccounts.create(email, "Karigar Buti", StaffRole.studio, "aakar-karigar");
        String[] studio = bearer(body(post("/admin/api/auth/login", "{\"email\": \"" + email + "\", \"password\": \"aakar-karigar\"}"))
                .get("access_token").asText());
        assertThat(body(get("/admin/api/motifs", studio))).isEqualTo(motifs);
        assertProblem(get("/admin/api/motifs"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/admin/api/motifs", bearer(signIn(phone()))), HttpStatus.UNAUTHORIZED, "unauthenticated");

        assertThat(body(get("/v3/api-docs")).get("paths").fieldNames()).toIterable()
                .contains("/api/motifs", "/api/motifs/{id}.svg", "/admin/api/motifs");
    }

    @Test
    void eachMotifsArtworkIsServedAsSvgWithCacheHeaders() throws IOException {
        Path folder = Contracts.designTokens("motifs");
        for (JsonNode entry : index.get("motifs")) {
            String id = entry.get("id").asText();
            ResponseEntity<byte[]> svg = getBytes("/api/motifs/" + id + ".svg");
            assertThat(svg.getStatusCode()).as(id).isEqualTo(HttpStatus.OK);
            assertThat(svg.getHeaders().getContentType()).isNotNull();
            assertThat(svg.getHeaders().getContentType().isCompatibleWith(MediaType.parseMediaType("image/svg+xml"))).as(id).isTrue();
            assertThat(svg.getBody()).as(id).isEqualTo(Files.readAllBytes(folder.resolve(entry.get("file").asText())));
            assertThat(svg.getHeaders().getCacheControl()).contains("max-age=86400").contains("public");
            assertThat(svg.getHeaders().getETag()).isNotBlank();
            assertThat(svg.getHeaders().getFirst("Content-Security-Policy")).contains("default-src 'none'");
        }
        // a browser holding the artwork revalidates without downloading it again
        String etag = getBytes("/api/motifs/lotus.svg").getHeaders().getETag();
        ResponseEntity<byte[]> again = getBytes("/api/motifs/lotus.svg", "If-None-Match", etag);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(again.getBody()).isNullOrEmpty();
    }

    @Test
    void anUnknownMotifIs404AndPathTricksNeverReachAFile() {
        JsonNode unknown = assertProblem(get("/api/motifs/peacock.svg"), HttpStatus.NOT_FOUND, "unknown_motif");
        assertThat(unknown.get("detail").asText()).isEqualTo("Motif peacock was not found");
        // the id is a key of the index, never a path: whatever reaches the controller is an unknown motif
        for (String path : List.of("/api/motifs/index.svg", "/api/motifs/...svg", "/api/motifs/Lotus.svg", "/api/motifs/lotus.svg.svg")) {
            assertProblem(raw(path), HttpStatus.NOT_FOUND, "unknown_motif");
        }
        // encoded slashes and backslashes, dot segments and doubled slashes are refused before routing (Tomcat, then Spring
        // Security's firewall answer 400 for them today); either way nothing is served
        for (String path : List.of("/api/motifs/..%2Findex.svg", "/api/motifs/..%2F..%2F..%2Fsettings.gradle.kts.svg", "/api/motifs/%2e%2e%2findex.svg",
                "/api/motifs/..%252Findex.svg", "/api/motifs/%2E%2E.svg", "/api/motifs/..%5Cindex.svg", "/api/motifs/../index.json",
                "/api/motifs/..;/index.json", "/api/motifs//lotus.svg")) {
            ResponseEntity<String> refused = raw(path);
            assertThat(refused.getStatusCode().is4xxClientError()).as("%s answered %s", path, refused.getStatusCode()).isTrue();
            assertThat(refused.getBody()).as(path).doesNotContain("<svg", "min_scale", "reference_mm", "rootProject");
        }
    }

    @Test
    void aDesignWithAnUnknownMotifOrACrowdedSpotIsRefusedAtCreateTime() {
        String[] guest = guest(UUID.randomUUID());
        String photo = uploaded(guest, "photo.png", "image", SampleFiles.png()).get("id").asText();

        JsonNode unknown = assertProblem(post("/api/designs", CREATE.formatted("{\"type\": \"motif\", \"motif_id\": \"peacock\", \"anchor\": \"face\"}"),
                guest), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertThat(unknown.get("detail").asText()).isEqualTo("We don't have a motif (Buti) called “peacock”; choose one from the motif library");
        assertThat(unknown.get("field").asText()).isEqualTo("features[0].motif_id");
        assertThat(unknown.get("template_id").asText()).isEqualTo("keychain_tag");

        JsonNode tooSmall = assertProblem(post("/api/designs", CREATE.formatted(
                "{\"type\": \"motif\", \"motif_id\": \"paisley\", \"anchor\": \"face\", \"scale\": 0.25}"), guest), HttpStatus.UNPROCESSABLE_ENTITY,
                "param_out_of_range");
        assertThat(tooSmall.get("detail").asText()).isEqualTo("The Paisley motif (Buti) can't be printed smaller than scale 0.3; choose a larger scale");
        assertThat(tooSmall.get("params")).extracting(JsonNode::asText).containsExactly("features[0].scale");

        JsonNode crowded = assertProblem(post("/api/designs", CREATE.formatted(RELIEF.formatted(photo, "face")
                + ", {\"type\": \"motif\", \"motif_id\": \"lotus\", \"anchor\": \"face\"}"), guest), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertThat(crowded.get("detail").asText())
                .isEqualTo("The Face is too crowded for a photo relief (Chhavi) and a motif (Buti) together; put the motif (Buti) on another spot");
        assertThat(crowded.get("anchor").asText()).isEqualTo("face");
        assertThat(crowded.get("feature").asInt()).isEqualTo(1);
        JsonNode crowdedByText = assertProblem(post("/api/designs", CREATE.formatted("{\"type\": \"emboss_text\", \"text\": \"Asha\", \"anchor\": \"face\"}, "
                + RELIEF.formatted(photo, "face")), guest), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertThat(crowdedByText.get("detail").asText())
                .isEqualTo("The Face is too crowded for a photo relief (Chhavi) and text (Naam) together; put the text (Naam) on another spot");
        // none of them reached the geometry service
        assertThat(GEOMETRY.findAll(postRequestedFor(urlEqualTo("/v1/build")).withRequestBody(containing("peacock")))).isEmpty();

        // a text and a motif side by side on the face and the photo on the back start a build, with the contract defaults
        ResponseEntity<String> accepted = post("/api/designs", CREATE.formatted("{\"type\": \"emboss_text\", \"text\": \"Asha\", \"anchor\": \"face\"}, "
                + "{\"type\": \"motif\", \"motif_id\": \"star_rangoli\", \"anchor\": \"face\", \"scale\": 0.5}, " + RELIEF.formatted(photo, "back")), guest);
        assertThat(accepted.getStatusCode()).as(accepted.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode features = body(get("/api/designs/" + body(accepted).get("design_id").asText(), guest)).get("latest_version").get("spec").get("features");
        assertThat(features).hasSize(3);
        assertThat(features.get(1).get("motif_id").asText()).isEqualTo("star_rangoli");
        assertThat(features.get(1).get("scale").asDouble()).isEqualTo(0.5);
        assertThat(features.get(1).get("depth_mm").asDouble()).isEqualTo(1.0);
        assertThat(features.get(1).get("mode").asText()).isEqualTo("deboss");
        awaitJob(body(accepted).get("job_id").asText(), "succeeded");
    }

    /** GET a path exactly as written (percent-escapes included), never throwing on 4xx/5xx. */
    private ResponseEntity<String> raw(String path) {
        return api().get().uri(URI.create(baseUrl() + path)).retrieve().toEntity(String.class);
    }
}
