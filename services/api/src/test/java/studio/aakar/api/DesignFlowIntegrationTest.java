package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.Contracts;
import studio.aakar.api.support.GeometryStub;

/** The Phase 0 vertical slice against Postgres with the geometry service stubbed by WireMock. */
class DesignFlowIntegrationTest extends AbstractIntegrationTest {

    private static final Pattern SSE_ID = Pattern.compile("^id:\\s*(\\d+)$", Pattern.MULTILINE);
    private static final Pattern SSE_DATA = Pattern.compile("^data:\\s*(\\{.*})$", Pattern.MULTILINE);

    @Test
    void catalogListsTheSixShopItemsAndSixMaterials() {
        ResponseEntity<String> items = get("/api/catalog/items");
        assertThat(items.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode list = body(items);
        assertThat(list).hasSize(6);
        assertThat(list.findValuesAsText("slug")).containsExactlyInAnyOrder("jharokha-phone-stand", "ajrakh-coasters", "fluted-planter",
                "kantha-nameplate", "elephant-bookends", "pillar-headphone-stand");
        assertThat(list.get(0).get("slug").asText()).isEqualTo("jharokha-phone-stand"); // the only available one sorts first

        assertThat(body(get("/api/catalog/items?category=desk_tech"))).hasSize(2);
        assertThat(body(get("/api/catalog/items?q=coaster"))).singleElement()
                .satisfies(i -> assertThat(i.get("specs_line").asText()).isEqualTo("100 mm · heat-safe to 90 °C · 28 g each"));

        JsonNode stand = body(get("/api/catalog/items/jharokha-phone-stand"));
        assertThat(stand.get("template_id").asText()).isEqualTo("jharokha_phone_stand");
        assertThat(stand.get("base_price_paise").asLong()).isEqualTo(49_900);
        assertThat(stand.get("default_params").get("width_mm").asInt()).isEqualTo(92);
        assertThat(stand.get("default_material").asText()).isEqualTo("terracotta_silk");
        assertThat(stand.get("available").asBoolean()).isTrue();
        assertThat(stand.get("category").asText()).isEqualTo("desk_tech");

        ResponseEntity<String> missing = get("/api/catalog/items/nope");
        assertProblem(missing, HttpStatus.NOT_FOUND, "not_found");

        JsonNode materials = body(get("/api/catalog/materials"));
        assertThat(materials).hasSize(6);
        assertThat(materials.get(2).get("id").asText()).isEqualTo("terracotta_silk");
        assertThat(materials.get(2).get("rate_per_g_paise").asInt()).isEqualTo(463);
        assertThat(materials.get(2).get("pbr").get("sheen_color").asText()).isEqualTo("#E8B48F");
        assertThat(materials.get(2).get("finish_class").asText()).isEqualTo("silk");
    }

    @Test
    void templatesComeFromTheGeometryService() {
        JsonNode all = body(get("/api/templates"));
        assertThat(all).singleElement().satisfies(d -> {
            assertThat(d.get("id").asText()).isEqualTo("jharokha_phone_stand");
            assertThat(d.get("params").get("width_mm").get("default").asInt()).isEqualTo(92);
            assertThat(d.get("anchors")).hasSize(3);
            assertThat(d.get("constraints").get("bed_mm")).hasSize(3);
        });
        assertThat(body(get("/api/templates/jharokha_phone_stand")).get("family").asText()).isEqualTo("phone_stand");
        assertProblem(get("/api/templates/nope"), HttpStatus.NOT_FOUND, "not_found");
    }

    @Test
    void shopDesignIsGeneratedPricedAndEditable() {
        ResponseEntity<String> accepted = post("/api/designs", """
                {"source": "shop", "catalog_item_slug": "jharokha-phone-stand"}
                """);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode a = body(accepted);
        String designId = a.get("design_id").asText();
        String jobId = a.get("job_id").asText();
        assertThat(a.get("version_no").asInt()).isEqualTo(1);
        assertThat(a.get("events_url").asText()).isEqualTo("/api/jobs/" + jobId + "/events");

        awaitJob(jobId, "succeeded");

        JsonNode job = body(get("/api/jobs/" + jobId));
        assertThat(job.get("stage").asText()).isEqualTo("ready");
        assertThat(job.get("type").asText()).isEqualTo("generate");
        assertThat(job.get("attempts").asInt()).isEqualTo(1);
        assertThat(job.get("started_at").asText()).isNotBlank();
        assertThat(job.get("finished_at").asText()).isNotBlank();

        JsonNode design = body(get("/api/designs/" + designId));
        assertThat(design.get("status").asText()).isEqualTo("ready");
        assertThat(design.get("source").asText()).isEqualTo("shop");
        assertThat(design.get("catalog_item_slug").asText()).isEqualTo("jharokha-phone-stand");
        assertThat(design.get("title").asText()).isEqualTo("Jharokha Phone Stand");
        assertThat(design.get("versions_count").asInt()).isEqualTo(1);
        JsonNode latest = design.get("latest_version");
        String versionId = latest.get("id").asText();
        assertThat(latest.get("status").asText()).isEqualTo("ready");
        assertThat(latest.get("created_by").asText()).isEqualTo("user");
        assertThat(latest.get("job_id").asText()).isEqualTo(jobId);
        assertThat(latest.get("template").get("id").asText()).isEqualTo("jharokha_phone_stand");
        assertThat(latest.get("assets").get("glb").get("url").asText()).contains("/assets/designs/" + designId + "/v1/model.glb");
        assertThat(latest.get("assets").get("3mf").get("content_type").asText()).isEqualTo("model/3mf");
        assertThat(latest.get("geometry").get("bounds_mm")).hasSize(3);
        assertThat(latest.get("printability").get("passed").asBoolean()).isTrue();
        assertThat(latest.get("printability").get("checks").get("thinnest_wall").get("value").asDouble()).isEqualTo(3.2);
        assertThat(latest.get("print_estimate").get("print_seconds").asInt()).isEqualTo(13_200);
        assertThat(latest.get("karigar_note").asText()).startsWith("A jharokha-arch phone stand");
        assertThat(latest.get("spec").get("template").asText()).isEqualTo("jharokha_phone_stand@1");
        assertThat(latest.get("spec").get("material").asText()).isEqualTo("terracotta_silk");
        assertThat(latest.get("price").get("total_paise").asLong() % 1000).isEqualTo(900);
        validateAgainst("schemas/design-spec.v1.json", latest.get("spec"));
        validateAgainst("schemas/printability-report.v1.json", latest.get("printability"));
        validateAgainst("schemas/print-estimate.v1.json", latest.get("print_estimate"));

        assertThat(body(get("/api/designs/" + designId + "/versions"))).hasSize(1);
        assertThat(body(get("/api/versions/" + versionId)).get("version_no").asInt()).isEqualTo(1);

        // Price for a material: 64 g × ₹4.63 = ₹296 + ₹733 + ₹120 = ₹1,149
        ResponseEntity<String> priced = get("/api/versions/" + versionId + "/price?material=terracotta_silk");
        assertThat(priced.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode price = body(priced);
        assertThat(price.get("currency").asText()).isEqualTo("INR");
        assertThat(price.get("material_id").asText()).isEqualTo("terracotta_silk");
        assertThat(price.get("total_paise").asLong() % 1000).isEqualTo(900);
        assertThat(price.get("total_paise").asLong()).isEqualTo(114_900);
        assertThat(price.get("shipping_paise").asLong()).isZero();
        assertThat(price.get("lines")).extracting(l -> l.get("code").asText()).containsExactly("material", "machine_time", "finishing");
        assertThat(price.get("lines").get(0).get("label").asText()).isEqualTo("Material · 64 g");
        validateAgainst("schemas/price-breakdown.v1.json", price);
        // A matte swap re-prices without re-slicing
        assertThat(body(get("/api/versions/" + versionId + "/price?material=indigo_matte")).get("lines").get(2).get("label").asText())
                .isEqualTo("Hand finishing");
        assertProblem(get("/api/versions/" + versionId + "/price?material=unobtainium"), HttpStatus.NOT_FOUND, "unknown_material");
        assertProblem(get("/api/versions/" + versionId + "/price"), HttpStatus.BAD_REQUEST, "validation_failed");

        JsonNode report = body(get("/api/versions/" + versionId + "/printability"));
        assertThat(report.get("report_version").asText()).isEqualTo("1.0");
        assertThat(report.get("checks").get("centre_of_gravity").get("summary").asText()).isEqualTo("Inside base · 3 mm");

        // Out-of-range edit is rejected, never clamped
        ResponseEntity<String> tooWide = post("/api/versions/" + versionId + "/params", """
                {"params": {"width_mm": 200}}
                """);
        JsonNode problem = assertProblem(tooWide, HttpStatus.UNPROCESSABLE_ENTITY, "param_out_of_range");
        assertThat(problem.get("params")).extracting(JsonNode::asText).containsExactly("width_mm");
        assertThat(problem.get("detail").asText()).contains("width_mm").contains("70").contains("110");
        assertThat(body(get("/api/designs/" + designId)).get("versions_count").asInt()).isEqualTo(1);

        // A valid edit creates version 2 with a new job
        ResponseEntity<String> edited = post("/api/versions/" + versionId + "/params", """
                {"params": {"width_mm": 100, "arch_cusps": 6}, "material": "polished_brass"}
                """);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode e = body(edited);
        assertThat(e.get("design_id").asText()).isEqualTo(designId);
        assertThat(e.get("version_no").asInt()).isEqualTo(2);
        awaitJob(e.get("job_id").asText(), "succeeded");

        JsonNode versions = body(get("/api/designs/" + designId + "/versions"));
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).get("version_no").asInt()).isEqualTo(2);
        assertThat(versions.get(0).get("parent_version_id").asText()).isEqualTo(versionId);
        assertThat(versions.get(0).get("status").asText()).isEqualTo("ready");
        assertThat(body(get("/api/designs/" + designId)).get("versions_count").asInt()).isEqualTo(2);

        // SSE replay of the finished first job
        HttpResponse<String> stream = sse(jobId, null);
        assertThat(stream.statusCode()).isEqualTo(200);
        assertThat(stream.headers().firstValue("Content-Type").orElse("")).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(stream.body()).containsPattern("event:\\s*stage");
        assertThat(sseIds(stream.body())).containsExactly(1, 2, 3);
        List<JsonNode> events = sseEvents(stream.body());
        assertThat(events).extracting(n -> n.get("stage").asText()).containsExactly("queued", "pricing", "ready");
        assertThat(events.get(2).get("version_id").asText()).isEqualTo(versionId);
        assertThat(events.get(2).get("job_id").asText()).isEqualTo(jobId);
        assertThat(events.get(2).get("sequence").asInt()).isEqualTo(3);
        assertThat(events.get(2).get("at").asText()).endsWith("Z");

        // Reconnect with Last-Event-ID replays only what follows
        HttpResponse<String> resumed = sse(jobId, "2");
        assertThat(sseIds(resumed.body())).containsExactly(3);
        HttpResponse<String> caughtUp = sse(jobId, "3");
        assertThat(caughtUp.statusCode()).isEqualTo(200);
        assertThat(sseIds(caughtUp.body())).isEmpty();
    }

    @Test
    void openApiDocumentUsesTheContractsNaming() {
        JsonNode docs = body(get("/v3/api-docs"));
        assertThat(docs.get("info").get("title").asText()).isEqualTo("Aakar API");
        assertThat(docs.get("paths").fieldNames()).toIterable().contains("/api/designs", "/api/jobs/{jobId}/events",
                "/api/versions/{versionId}/price", "/internal/jobs/{jobId}/callback");
        JsonNode request = docs.get("components").get("schemas").get("CreateDesignRequest").get("properties");
        assertThat(request.fieldNames()).toIterable().contains("source", "catalog_item_slug", "template_id", "params", "material", "prompt", "title");
        assertThat(get("/swagger-ui/index.html").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void createFromPromptIsNotYetAvailable() {
        ResponseEntity<String> response = post("/api/designs", """
                {"source": "create", "prompt": "a lotus lamp for my balcony"}
                """);
        JsonNode problem = assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "not_yet_available");
        assertThat(problem.get("detail").asText())
                .isEqualTo("Create from a description arrives in Phase 2; start from a template or a Shop piece instead.");
        // even with a template alongside
        assertProblem(post("/api/designs", """
                {"source": "remix", "template_id": "jharokha_phone_stand", "prompt": "make it taller"}
                """), HttpStatus.UNPROCESSABLE_ENTITY, "not_yet_available");
    }

    @Test
    void requestProblemsCarryStableCodes() {
        assertProblem(post("/api/designs", "{}"), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/designs", "{\"source\": \"bazaar\"}"), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/designs", "not json"), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/designs", "{\"source\": \"shop\"}"), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/designs", "{\"source\": \"remix\"}"), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/designs", "{\"source\": \"shop\", \"catalog_item_slug\": \"nope\"}"), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(post("/api/designs", "{\"source\": \"shop\", \"catalog_item_slug\": \"ajrakh-coasters\"}"),
                HttpStatus.UNPROCESSABLE_ENTITY, "template_not_available");
        assertProblem(post("/api/designs", "{\"source\": \"remix\", \"template_id\": \"lotus_lamp\"}"), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(post("/api/designs", "{\"source\": \"remix\", \"template_id\": \"jharokha_phone_stand\", \"material\": \"gold_leaf\"}"),
                HttpStatus.UNPROCESSABLE_ENTITY, "unknown_material");
        assertProblem(post("/api/designs", "{\"source\": \"remix\", \"template_id\": \"jharokha_phone_stand\", \"params\": {\"tilt_deg\": 90}}"),
                HttpStatus.UNPROCESSABLE_ENTITY, "param_out_of_range");
        assertProblem(get("/api/designs/" + UUID.randomUUID()), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(get("/api/designs/not-a-uuid"), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(get("/api/versions/" + UUID.randomUUID()), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(get("/api/jobs/" + UUID.randomUUID()), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(get("/api/nowhere"), HttpStatus.NOT_FOUND, "not_found");
        UUID unknownJob = UUID.randomUUID();
        assertProblem(post("/internal/jobs/" + unknownJob + "/callback", progressEnvelope(unknownJob, UUID.randomUUID(), "sculpting", 40)),
                HttpStatus.NOT_FOUND, "not_found");
    }

    @Test
    void failedBuildMarksVersionAndJobFailed() {
        ResponseEntity<String> accepted = post("/api/designs", """
                {"source": "create", "template_id": "jharokha_phone_stand", "params": {"arch_cusps": %d}, "title": "Seven cusps"}
                """.formatted(GeometryStub.FAILING_CUSPS));
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode a = body(accepted);
        String jobId = a.get("job_id").asText();
        String designId = a.get("design_id").asText();

        awaitJob(jobId, "failed");

        JsonNode job = body(get("/api/jobs/" + jobId));
        assertThat(job.get("stage").asText()).isEqualTo("failed");
        assertThat(job.get("error_code").asText()).isEqualTo("not_printable");
        assertThat(job.get("message").asText()).isEqualTo("Seven cusps leave the arch too thin to print.");

        JsonNode design = body(get("/api/designs/" + designId));
        assertThat(design.get("status").asText()).isEqualTo("failed");
        assertThat(design.get("title").asText()).isEqualTo("Seven cusps");
        assertThat(design.get("source").asText()).isEqualTo("create");
        assertThat(design.get("catalog_item_slug").isNull()).isTrue();
        String versionId = design.get("latest_version").get("id").asText();
        assertThat(design.get("latest_version").get("status").asText()).isEqualTo("failed");
        assertThat(design.get("latest_version").get("spec").get("params").get("arch_cusps").asInt()).isEqualTo(7);
        assertThat(design.get("latest_version").get("spec").get("params").get("width_mm").asInt()).isEqualTo(92); // defaults filled in

        assertProblem(get("/api/versions/" + versionId + "/printability"), HttpStatus.CONFLICT, "version_not_ready");
        assertProblem(get("/api/versions/" + versionId + "/price?material=terracotta_silk"), HttpStatus.CONFLICT, "version_not_ready");

        List<JsonNode> events = sseEvents(sse(jobId, null).body());
        assertThat(events).extracting(n -> n.get("stage").asText()).containsExactly("queued", "failed");
        assertThat(events.get(1).get("error_code").asText()).isEqualTo("not_printable");
    }

    @Test
    void sseStreamsLiveProgressAndCallbacksAreIdempotent() throws Exception {
        ResponseEntity<String> accepted = post("/api/designs", """
                {"source": "remix", "template_id": "jharokha_phone_stand", "params": {"arch_cusps": %d}}
                """.formatted(GeometryStub.SLOW_BUILD_CUSPS));
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode a = body(accepted);
        UUID jobId = UUID.fromString(a.get("job_id").asText());
        UUID designId = UUID.fromString(a.get("design_id").asText());

        // While the (slow) build runs, geometry reports progress; the same event twice is applied once.
        String understanding = progressEnvelope(jobId, designId, "understanding", 10);
        assertThat(post("/internal/jobs/" + jobId + "/callback", understanding).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/internal/jobs/" + jobId + "/callback", understanding).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(post("/internal/jobs/" + jobId + "/callback", progressEnvelope(jobId, designId, "sculpting", 45)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertProblem(post("/internal/jobs/" + jobId + "/callback", progressEnvelope(UUID.randomUUID(), designId, "checking", 80)),
                HttpStatus.BAD_REQUEST, "validation_failed");

        JsonNode running = body(get("/api/jobs/" + jobId));
        assertThat(running.get("status").asText()).isIn("queued", "running");
        assertThat(running.get("stage").asText()).isEqualTo("sculpting");
        assertProblem(get("/api/versions/" + body(get("/api/designs/" + designId)).get("latest_version").get("id").asText() + "/printability"),
                HttpStatus.CONFLICT, "version_not_ready");

        // Connect now: history is replayed, then pricing/ready arrive live and close the stream.
        HttpResponse<String> stream = sse(jobId.toString(), null);
        assertThat(stream.statusCode()).isEqualTo(200);
        List<JsonNode> events = sseEvents(stream.body());
        assertThat(events).extracting(n -> n.get("stage").asText()).containsExactly("queued", "understanding", "sculpting", "pricing", "ready");
        assertThat(sseIds(stream.body())).containsExactly(1, 2, 3, 4, 5);
        assertThat(events.get(1).get("percent").asInt()).isEqualTo(10);
        assertThat(events.get(1).get("message").asText()).isEqualTo("Understanding your idea");
        assertThat(events.get(4).get("version_id").asText()).isNotBlank();

        JsonNode job = body(get("/api/jobs/" + jobId));
        assertThat(job.get("status").asText()).isEqualTo("succeeded");
        // Late or repeated messages after completion are ignored
        assertThat(post("/internal/jobs/" + jobId + "/callback", progressEnvelope(jobId, designId, "checking", 80)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(sseIds(sse(jobId.toString(), null).body())).containsExactly(1, 2, 3, 4, 5);
        assertThat(body(get("/api/designs/" + designId)).get("status").asText()).isEqualTo("ready");
    }

    private HttpResponse<String> sse(String jobId, String lastEventId) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/jobs/" + jobId + "/events"))
                    .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                    .timeout(Duration.ofSeconds(20))
                    .GET();
            if (lastEventId != null) {
                request.header("Last-Event-ID", lastEventId);
            }
            return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                    .send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static List<Integer> sseIds(String body) {
        Matcher m = SSE_ID.matcher(body);
        List<Integer> ids = new java.util.ArrayList<>();
        while (m.find()) {
            ids.add(Integer.parseInt(m.group(1)));
        }
        return ids;
    }

    private List<JsonNode> sseEvents(String body) {
        Matcher m = SSE_DATA.matcher(body);
        List<JsonNode> events = new java.util.ArrayList<>();
        while (m.find()) {
            try {
                events.add(json.readTree(m.group(1)));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return events;
    }

    private static String progressEnvelope(UUID jobId, UUID designId, String stage, int percent) {
        return """
                {"type": "design.progress", "version": 1, "event_id": "%s", "job_id": "%s", "design_id": "%s",
                 "occurred_at": "2026-09-26T10:00:00Z", "sequence": %d,
                 "payload": {"stage": "%s", "message": "%s", "percent": %d}}
                """.formatted(UUID.nameUUIDFromBytes((jobId + stage).getBytes()), jobId, designId, percent, stage,
                switch (stage) {
                    case "understanding" -> "Understanding your idea";
                    case "sculpting" -> "Weaving your design";
                    default -> "Checking physics";
                }, percent);
    }

    private static void validateAgainst(String schema, JsonNode document) {
        Path file = Contracts.contracts(schema);
        if (Files.exists(file)) {
            assertThat(Contracts.validate(file, document)).as("%s violations", schema).isEmpty();
        }
    }
}
