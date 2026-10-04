package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.aakar.api.shared.ProblemCodes;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.GeometryStub;
import studio.aakar.api.support.SampleFiles;

/**
 * Swaroop, "print as it is" (ADR-0014, plan §4, walk 2): the customer's own model file is the whole piece. The design is
 * {@code source: upload}, family {@code raw_print}, no template params, and exactly one {@code hero_mesh} whose
 * {@code longest_mm} sits inside the family envelope (20–240 mm) — rejected outside it, never clamped. The price adds
 * the setup line; a file the geometry service cannot repair fails the job with {@code content_unusable}.
 */
class RawPrintIntegrationTest extends AbstractIntegrationTest {

    static final String SWAROOP = """
            {"source": "upload", "family_id": "raw_print", "params": {}, "material": "basic_white", "title": "Vase",
             "features": [%s]}
            """;
    static final String HERO = "{\"type\": \"hero_mesh\", \"source\": {\"upload_id\": \"%s\"}, \"anchor\": \"body\", \"fit\": \"longest\", \"longest_mm\": %s}";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void yourOwnModelIsSizedBuiltAndPricedWithTheSetupLine() {
        String[] guest = guest(UUID.randomUUID());
        String model = uploaded(guest, "vase.stl", "model", SampleFiles.binaryStl()).get("id").asText();

        ResponseEntity<String> accepted = post("/api/designs", SWAROOP.formatted(
                "{\"type\": \"hero_mesh\", \"source\": {\"upload_id\": \"" + model + "\"}, \"anchor\": \"body\", \"fit\": \"longest\", \"longest_mm\": 80, "
                        + "\"orientation\": \"lay_flat\"}"), guest);
        assertThat(accepted.getStatusCode()).as(accepted.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        awaitJob(body(accepted).get("job_id").asText(), "succeeded");

        JsonNode design = body(get("/api/designs/" + body(accepted).get("design_id").asText()));
        assertThat(design.get("status").asText()).isEqualTo("ready");
        assertThat(design.get("source").asText()).isEqualTo("upload");
        assertThat(design.get("family_id").asText()).isEqualTo("raw_print");
        assertThat(design.get("title").asText()).isEqualTo("Vase");
        JsonNode version = design.get("latest_version");
        assertThat(version.get("family_id").asText()).isEqualTo("raw_print");
        assertThat(version.get("hardware")).isEmpty();
        JsonNode spec = version.get("spec");
        assertThat(spec.get("template").asText()).isEqualTo("raw_print@1");
        assertThat(spec.get("params")).isEmpty();
        JsonNode hero = spec.get("features").get(0);
        assertThat(hero.get("type").asText()).isEqualTo("hero_mesh");
        assertThat(hero.get("anchor").asText()).isEqualTo("body");
        assertThat(hero.get("fit").asText()).isEqualTo("longest");
        assertThat(hero.get("longest_mm").asInt()).isEqualTo(80);
        assertThat(hero.get("yaw_deg").asInt()).isZero();
        assertThat(hero.get("orientation").asText()).isEqualTo("lay_flat");
        assertThat(hero.get("source").get("url").asText()).startsWith("http://api.internal:8080/media/uploads/").endsWith("/" + model + ".stl");
        assertThat(hero.get("source").get("format").asText()).isEqualTo("stl");

        // 20 cm³ × 1.24 = 24.8 g × ₹3.80 = ₹94; 1 h 30 m × ₹200 = ₹300; matte ₹80; setup ₹99 → ₹573 → ₹579, above the ₹349 minimum
        JsonNode price = version.get("price");
        assertThat(price.get("family_id").asText()).isEqualTo("raw_print");
        assertThat(price.get("lines").findValuesAsText("code")).containsExactly("material", "machine_time", "finishing", "setup");
        assertThat(price.get("lines").get(3).get("label").asText()).isEqualTo("Studio setup");
        assertThat(price.get("lines").get(3).get("amount_paise").asLong()).isEqualTo(9_900);
        assertThat(price.get("subtotal_paise").asLong()).isEqualTo(57_900);
        assertThat(price.has("minimum_subtotal_paise")).isFalse();

        // a bigger print is a new version of the same form (the size lives on the feature; there are no params to change)
        ResponseEntity<String> bigger = post("/api/versions/" + version.get("id").asText() + "/params",
                "{\"params\": {}, \"features\": [" + HERO.formatted(model, 120) + "]}", guest);
        assertThat(bigger.getStatusCode()).as(bigger.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        awaitJob(body(bigger).get("job_id").asText(), "succeeded");
        JsonNode v2 = body(get("/api/designs/" + body(accepted).get("design_id").asText())).get("latest_version");
        assertThat(v2.get("spec").get("features").get(0).get("longest_mm").asInt()).isEqualTo(120);
        // a finish swap keeps the form as it was
        ResponseEntity<String> silk = post("/api/versions/" + v2.get("id").asText() + "/params", "{\"params\": {}, \"material\": \"terracotta_silk\"}", guest);
        assertThat(silk.getStatusCode()).as(silk.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        awaitJob(body(silk).get("job_id").asText(), "succeeded");
        assertThat(body(get("/api/designs/" + body(accepted).get("design_id").asText())).get("latest_version").get("spec").get("features"))
                .isEqualTo(v2.get("spec").get("features"));
    }

    @Test
    void theModelAndItsSizeAreRequiredAndNeverClamped() {
        String[] guest = guest(UUID.randomUUID());
        String model = uploaded(guest, "vase.obj", "model", SampleFiles.obj()).get("id").asText();
        String photo = uploaded(guest, "photo.png", "image", SampleFiles.png()).get("id").asText();

        JsonNode missing = assertProblem(post("/api/designs", SWAROOP.formatted(""), guest), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertThat(missing.get("detail").asText()).isEqualTo("Add your model file to print it as it is");
        JsonNode tooSmall = assertProblem(post("/api/designs", SWAROOP.formatted(HERO.formatted(model, 10)), guest), HttpStatus.UNPROCESSABLE_ENTITY,
                "param_out_of_range");
        assertThat(tooSmall.get("detail").asText()).isEqualTo("Your model can be printed 20–240 mm on its longest side; 10 mm was asked");
        assertThat(tooSmall.get("params")).extracting(JsonNode::asText).containsExactly("features[0].longest_mm");
        assertProblem(post("/api/designs", SWAROOP.formatted(HERO.formatted(model, 245)), guest), HttpStatus.UNPROCESSABLE_ENTITY, "param_out_of_range");
        JsonNode unsized = assertProblem(post("/api/designs", SWAROOP.formatted(
                "{\"type\": \"hero_mesh\", \"source\": {\"upload_id\": \"" + model + "\"}, \"anchor\": \"body\"}"), guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertThat(unsized.get("detail").asText()).isEqualTo("Choose how long your model should be on its longest side (20–240 mm)");
        // Swaroop has no template params: size and orientation live on the form
        assertProblem(post("/api/designs", SWAROOP.replace("\"params\": {}", "\"params\": {\"longest_mm\": 80}").formatted(HERO.formatted(model, 80)), guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "param_out_of_range");
        // only a model file, only on the body, only through source upload
        assertProblem(post("/api/designs", SWAROOP.formatted(HERO.formatted(photo, 80)), guest), HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_format");
        assertProblem(post("/api/designs", SWAROOP.formatted(HERO.formatted(model, 80) + ", "
                + "{\"type\": \"relief_image\", \"source\": {\"upload_id\": \"" + photo + "\"}, \"anchor\": \"body\"}"), guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_feature");
        assertProblem(post("/api/designs", SWAROOP.replace("\"source\": \"upload\"", "\"source\": \"create\"").formatted(HERO.formatted(model, 80)), guest),
                HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/designs", "{\"source\": \"upload\", \"features\": [" + HERO.formatted(model, 80) + "]}", guest),
                HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/designs", SWAROOP.formatted(HERO.formatted(model, 80)), guest(UUID.randomUUID())), HttpStatus.NOT_FOUND, "not_found");
        // the edges of the envelope are fine
        assertThat(post("/api/designs", SWAROOP.formatted(HERO.formatted(model, 20)), guest).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(post("/api/designs", SWAROOP.formatted(HERO.formatted(model, 240)), guest).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void aModelTheGeometryServiceCannotRepairFailsWithContentUnusable() {
        String[] guest = guest(UUID.randomUUID());
        String model = uploaded(guest, "dragon.stl", "model", SampleFiles.asciiStl()).get("id").asText();
        // stand in for a file that cannot be repaired: the stub answers content_unusable for a model URL containing "broken"
        jdbc.update("update uploads set storage_key = replace(storage_key, id::text, ?) where id = ?::uuid",
                GeometryStub.UNUSABLE_MARKER + "-" + model, model);

        ResponseEntity<String> accepted = post("/api/designs", SWAROOP.formatted(HERO.formatted(model, 60)), guest);
        assertThat(accepted.getStatusCode()).as(accepted.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        String jobId = body(accepted).get("job_id").asText();
        awaitJob(jobId, "failed");

        JsonNode job = body(get("/api/jobs/" + jobId));
        assertThat(job.get("stage").asText()).isEqualTo("failed");
        assertThat(job.get("error_code").asText()).isEqualTo(ProblemCodes.CONTENT_UNUSABLE);
        assertThat(job.get("message").asText()).isEqualTo("We couldn't repair this model file into a solid we can print.");
        JsonNode design = body(get("/api/designs/" + body(accepted).get("design_id").asText()));
        assertThat(design.get("status").asText()).isEqualTo("failed");
        assertThat(design.get("latest_version").get("spec").get("features").get(0).get("source").get("url").asText()).contains(GeometryStub.UNUSABLE_MARKER);
        assertProblem(get("/api/versions/" + design.get("latest_version").get("id").asText() + "/price?material=basic_white"), HttpStatus.CONFLICT,
                "version_not_ready");
    }
}
