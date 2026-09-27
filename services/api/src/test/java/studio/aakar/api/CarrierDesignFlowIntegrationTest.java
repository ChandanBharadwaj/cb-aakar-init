package studio.aakar.api;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.Contracts;
import studio.aakar.api.support.SampleFiles;

/**
 * An outcome family end to end (plan §2, §5, walk 1): a guest uploads photos, starts a keychain (Saathi) with a photo
 * relief on each face, the geometry stub builds {@code keychain_tag@1} and reports its split ring; the version carries
 * the named hardware, the spec carries the photos by their internal URL, the price has the hardware line and the family
 * minimum; cart and checkout snapshot it; the print pack lists hardware and content and adds a packing list. Then the
 * magnet's reported hardware, edits that keep or replace the content, and the problems family and content requests get.
 */
class CarrierDesignFlowIntegrationTest extends AbstractIntegrationTest {

    static final String INTERNAL_MEDIA = "http://api.internal:8080/media/uploads/";
    static final String RELIEF = "{\"type\": \"relief_image\", \"source\": {\"upload_id\": \"%s\"}, \"anchor\": \"%s\"}";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aKeychainWithPhotosIsBuiltPricedOrderedAndPacked() throws IOException {
        UUID guestId = UUID.randomUUID();
        String[] guest = guest(guestId);
        String face = uploaded(guest, "asha.png", "image", SampleFiles.png()).get("id").asText();
        String back = uploaded(guest, "rangoli.jpg", "image", SampleFiles.jpeg()).get("id").asText();

        // the client's url and format are ignored: the API fills the content source from the upload itself
        ResponseEntity<String> accepted = post("/api/designs", """
                {"source": "create", "family_id": "keychain", "material": "indigo_matte", "title": "Asha's keychain",
                 "features": [
                   {"type": "relief_image", "source": {"upload_id": "%s", "url": "http://elsewhere.example/steal.png", "format": "gltf"}, "anchor": "face"},
                   {"type": "relief_image", "source": {"upload_id": "%s"}, "anchor": "back", "mode": "deboss", "relief_mm": 0.8}]}
                """.formatted(face, back), guest);
        assertThat(accepted.getStatusCode()).as(accepted.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        String designId = body(accepted).get("design_id").asText();
        awaitJob(body(accepted).get("job_id").asText(), "succeeded");

        JsonNode design = body(get("/api/designs/" + designId, guest));
        assertThat(design.get("status").asText()).isEqualTo("ready");
        assertThat(design.get("source").asText()).isEqualTo("create");
        assertThat(design.get("family_id").asText()).isEqualTo("keychain");
        assertThat(design.get("title").asText()).isEqualTo("Asha's keychain");
        JsonNode version = design.get("latest_version");
        String versionId = version.get("id").asText();
        assertThat(version.get("family_id").asText()).isEqualTo("keychain");
        assertThat(version.get("template").get("id").asText()).isEqualTo("keychain_tag");
        assertThat(version.get("hardware")).hasSize(1);
        assertThat(version.get("hardware").get(0).get("sku").asText()).isEqualTo("split_ring_25");
        assertThat(version.get("hardware").get(0).get("qty").asInt()).isEqualTo(1);
        assertThat(version.get("hardware").get(0).get("name").asText()).isEqualTo("Steel split ring 25 mm");

        // the spec names each photo by the URL the geometry service fetches, with the contract defaults filled in
        JsonNode spec = version.get("spec");
        assertThat(spec.get("template").asText()).isEqualTo("keychain_tag@1");
        assertThat(spec.get("family").asText()).isEqualTo("keychain");
        assertThat(spec.get("params").get("shape").asText()).isEqualTo("rounded");
        assertThat(spec.get("features")).hasSize(2);
        JsonNode first = spec.get("features").get(0);
        assertThat(first.get("anchor").asText()).isEqualTo("face");
        assertThat(first.get("mode").asText()).isEqualTo("emboss");
        assertThat(first.get("relief_mm").asDouble()).isEqualTo(0.6);
        assertThat(first.get("fit").asText()).isEqualTo("contain");
        assertThat(first.get("invert").asBoolean()).isFalse();
        assertThat(first.get("cutout").asText()).isEqualTo("none");
        assertThat(first.get("source").get("upload_id").asText()).isEqualTo(face);
        assertThat(first.get("source").get("url").asText()).startsWith(INTERNAL_MEDIA).endsWith("/" + face + ".png").doesNotContain(guestId.toString());
        assertThat(first.get("source").get("format").asText()).isEqualTo("png");
        assertThat(first.get("source").get("origin").asText()).isEqualTo("upload");
        JsonNode second = spec.get("features").get(1);
        assertThat(second.get("mode").asText()).isEqualTo("deboss");
        assertThat(second.get("relief_mm").asDouble()).isEqualTo(0.8);
        assertThat(second.get("source").get("format").asText()).isEqualTo("jpg");
        validateAgainst("schemas/design-spec.v1.json", spec);

        // exactly what the geometry service was asked to build
        List<LoggedRequest> builds = GEOMETRY.findAll(postRequestedFor(urlEqualTo("/v1/build")).withRequestBody(matchingJsonPath("$.design_id", equalTo(designId))));
        assertThat(builds).hasSize(1);
        JsonNode sent = json.readTree(builds.get(0).getBodyAsString()).get("spec");
        assertThat(sent.get("features").get(0).get("source").get("url").asText()).isEqualTo(first.get("source").get("url").asText());
        assertThat(sent.toString()).doesNotContain("elsewhere.example").doesNotContain("gltf");

        // price: the print, the split ring at cost + 30 %, no setup, then the keychain minimum
        // 4 cm³ × 1.24 = 4.96 g × ₹4.20 = ₹21; 30 min × ₹200 = ₹100; matte ₹80; ring ₹3 × 1.3 = ₹4 → ₹205 → ₹209 → minimum ₹249
        JsonNode price = version.get("price");
        assertThat(price.get("family_id").asText()).isEqualTo("keychain");
        assertThat(price.get("lines").findValuesAsText("code")).containsExactly("material", "machine_time", "finishing", "hardware");
        JsonNode hardwareLine = price.get("lines").get(3);
        assertThat(hardwareLine.get("label").asText()).isEqualTo("Steel split ring 25 mm · 1");
        assertThat(hardwareLine.get("amount_paise").asLong()).isEqualTo(400);
        assertThat(price.get("subtotal_paise").asLong()).isEqualTo(24_900);
        assertThat(price.get("minimum_subtotal_paise").asLong()).isEqualTo(24_900);
        assertThat(price.get("shipping_paise").asLong()).isEqualTo(7_900);
        assertThat(price.get("total_paise").asLong()).isEqualTo(32_800);
        validateAgainst("schemas/price-breakdown.v1.json", price);
        // in silk the lines reach the minimum by themselves (₹23 + ₹100 + ₹120 + ₹4 = ₹247 → ₹249), so it is not flagged
        JsonNode silk = body(get("/api/versions/" + versionId + "/price?material=terracotta_silk"));
        assertThat(silk.get("subtotal_paise").asLong()).isEqualTo(24_900);
        assertThat(silk.has("minimum_subtotal_paise")).isFalse();
        assertThat(silk.get("lines").findValuesAsText("code")).contains("hardware");

        // sign in (the design and both photos follow), cart and checkout snapshot the lines
        String token = signInAs(phone(), guestId);
        String[] customer = bearer(token);
        assertThat(get("/api/uploads/" + face, customer).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> added = post("/api/cart/items", cartItem(versionId, "indigo_matte", 2), customer);
        assertThat(added.getStatusCode()).as(added.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode line = body(added).get("items").get(0);
        assertThat(line.get("purchasable").asBoolean()).isTrue();
        assertThat(line.get("unit_price").get("lines").findValuesAsText("code")).containsExactly("material", "machine_time", "finishing", "hardware");
        assertThat(line.get("unit_price").get("minimum_subtotal_paise").asLong()).isEqualTo(24_900);
        assertThat(line.get("line_total_paise").asLong()).isEqualTo(49_800);
        assertThat(body(added).get("total_paise").asLong()).isEqualTo(49_800 + 7_900);
        String addressId = body(post("/api/me/addresses", ADDRESS_JSON.formatted("560001"), customer)).get("id").asText();
        ResponseEntity<String> checkedOut = post("/api/checkout", "{\"address_id\": \"" + addressId + "\"}", customer);
        assertThat(checkedOut.getStatusCode()).as(checkedOut.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode checkout = body(checkedOut);
        String orderId = checkout.get("order_id").asText();
        assertThat(post("/api/payments/" + checkout.get("payment").get("id").asText() + "/mock/complete", "{\"outcome\": \"success\", \"method\": \"upi\"}",
                customer).getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = body(get("/api/orders/" + orderId, customer));
        assertThat(order.get("status").asText()).isEqualTo("queued");
        JsonNode unitPrice = order.get("items").get(0).get("unit_price");
        assertThat(unitPrice.get("family_id").asText()).isEqualTo("keychain");
        assertThat(unitPrice.get("lines").get(3).get("label").asText()).isEqualTo("Steel split ring 25 mm · 1");
        assertThat(order.get("subtotal_paise").asLong()).isEqualTo(49_800);

        // the print pack: the sheet names the hardware and the content (never a file URL), the packing list adds up the rings
        ResponseEntity<byte[]> pack = getBytes("/admin/api/orders/" + orderId + "/print-pack", staffOwner());
        assertThat(pack.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, byte[]> files = unzip(pack.getBody());
        assertThat(files).containsKeys("item-1/print-sheet.txt", "item-1/model.3mf", "item-1/model.stl", "packing-list.txt");
        String sheet = new String(files.get("item-1/print-sheet.txt"), StandardCharsets.UTF_8);
        assertThat(sheet).contains("Template:       keychain_tag@1")
                .contains("Content:        photo relief (Chhavi) on face · photo relief (Chhavi) on back, deboss")
                .contains("Hardware:       split_ring_25 × 1 · Steel split ring 25 mm")
                .contains("Quantity:       2")
                .doesNotContain("/media/").doesNotContain(face).doesNotContain("api.internal");
        String packing = new String(files.get("packing-list.txt"), StandardCharsets.UTF_8);
        assertThat(packing).startsWith("AAKAR PACKING LIST · " + order.get("number").asText() + "\n")
                .contains("  - item 1 · Asha's keychain (design version 1) × 2 · Indigo Matte")
                .contains("Hardware:\n  - split_ring_25 × 2 · Steel split ring 25 mm\n");
    }

    @Test
    void theMagnetReportsItsMagnetsAndEditsKeepOrReplaceTheContent() {
        String[] guest = guest(UUID.randomUUID());
        String photo = uploaded(guest, "family.png", "image", SampleFiles.png()).get("id").asText();
        String another = uploaded(guest, "garden.jpg", "image", SampleFiles.jpeg()).get("id").asText();

        ResponseEntity<String> accepted = post("/api/designs", """
                {"source": "create", "family_id": "fridge_magnet", "params": {"magnet_count": 2}, "material": "basic_white",
                 "features": [%s]}
                """.formatted(RELIEF.formatted(photo, "face")), guest);
        assertThat(accepted.getStatusCode()).as(accepted.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        String designId = body(accepted).get("design_id").asText();
        JsonNode v1 = ready(body(accepted));
        // the geometry result is authoritative over the descriptor's one magnet
        assertThat(v1.get("hardware")).hasSize(1);
        assertThat(v1.get("hardware").get(0).get("sku").asText()).isEqualTo("magnet_d10x3");
        assertThat(v1.get("hardware").get(0).get("qty").asInt()).isEqualTo(2);
        assertThat(v1.get("hardware").get(0).get("name").asText()).isEqualTo("Neodymium disc magnet 10 × 3 mm");
        // ₹19 + ₹100 + ₹80 + 2 × ₹15 × 1.3 = ₹39 → ₹238 → ₹239, lifted to the magnet minimum ₹299
        JsonNode price = v1.get("price");
        assertThat(price.get("lines").get(3).get("label").asText()).isEqualTo("Neodymium disc magnet 10 × 3 mm · 2");
        assertThat(price.get("lines").get(3).get("amount_paise").asLong()).isEqualTo(3_900);
        assertThat(price.get("subtotal_paise").asLong()).isEqualTo(29_900);
        assertThat(price.get("minimum_subtotal_paise").asLong()).isEqualTo(29_900);

        // params only: the photo stays exactly as it was, the magnet count (and the hardware) follows the params
        JsonNode v2 = ready(body(post("/api/versions/" + v1.get("id").asText() + "/params", "{\"params\": {\"magnet_count\": 1}}", guest)));
        assertThat(v2.get("spec").get("features")).isEqualTo(v1.get("spec").get("features"));
        assertThat(v2.get("hardware").get(0).get("qty").asInt()).isEqualTo(1);
        assertThat(v2.get("parent_version_id").asText()).isEqualTo(v1.get("id").asText());

        // a new photo replaces the content; an empty list clears it
        JsonNode v3 = ready(body(post("/api/versions/" + v2.get("id").asText() + "/params",
                "{\"params\": {}, \"features\": [" + RELIEF.formatted(another, "face") + "]}", guest)));
        assertThat(v3.get("spec").get("features")).hasSize(1);
        assertThat(v3.get("spec").get("features").get(0).get("source").get("upload_id").asText()).isEqualTo(another);
        assertThat(v3.get("spec").get("features").get(0).get("source").get("format").asText()).isEqualTo("jpg");
        JsonNode v4 = ready(body(post("/api/versions/" + v3.get("id").asText() + "/params", "{\"params\": {}, \"features\": []}", guest)));
        assertThat(v4.get("spec").get("features")).isEmpty();
        assertThat(body(get("/api/designs/" + designId)).get("versions_count").asInt()).isEqualTo(4);

        // someone else's photo cannot be slipped in; a photo already on the version stays usable by whoever edits it
        String stranger = uploaded(guest(UUID.randomUUID()), "stranger.png", "image", SampleFiles.png()).get("id").asText();
        assertProblem(post("/api/versions/" + v3.get("id").asText() + "/params", "{\"params\": {}, \"features\": [" + RELIEF.formatted(stranger, "face") + "]}",
                guest), HttpStatus.NOT_FOUND, "not_found");
        assertThat(post("/api/versions/" + v3.get("id").asText() + "/params",
                "{\"params\": {}, \"features\": [{\"type\": \"relief_image\", \"source\": {\"upload_id\": \"" + another + "\"}, \"anchor\": \"face\", \"relief_mm\": 1.0}]}")
                .getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertProblem(post("/api/versions/" + v4.get("id").asText() + "/params", "{\"params\": {}, \"features\": [" + RELIEF.formatted(photo, "face") + "]}"),
                HttpStatus.NOT_FOUND, "not_found"); // cleared from v4, and an anonymous caller owns nothing

        // edits are checked like a new design
        JsonNode deep = assertProblem(post("/api/versions/" + v3.get("id").asText() + "/params",
                "{\"params\": {}, \"features\": [{\"type\": \"relief_image\", \"source\": {\"upload_id\": \"" + another + "\"}, \"anchor\": \"face\", \"relief_mm\": 2}]}",
                guest), HttpStatus.UNPROCESSABLE_ENTITY, "param_out_of_range");
        assertThat(deep.get("params")).extracting(JsonNode::asText).containsExactly("features[0].relief_mm");
        assertProblem(post("/api/versions/" + v3.get("id").asText() + "/params", "{\"params\": {\"magnet_count\": 3}}", guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "param_out_of_range");
        assertProblem(post("/api/versions/" + v3.get("id").asText() + "/params", "{\"params\": {}, \"features\": [" + "{},".repeat(8) + "{}]}", guest),
                HttpStatus.BAD_REQUEST, "validation_failed");
    }

    @Test
    void familyAndContentProblemsCarryStableCodes() {
        String[] guest = guest(UUID.randomUUID());
        String photo = uploaded(guest, "photo.png", "image", SampleFiles.png()).get("id").asText();
        String model = uploaded(guest, "vase.stl", "model", SampleFiles.binaryStl()).get("id").asText();
        String stranger = uploaded(guest(UUID.randomUUID()), "stranger.png", "image", SampleFiles.png()).get("id").asText();
        String create = "{\"source\": \"create\", \"family_id\": \"%s\", \"features\": [%s]}";

        assertProblem(post("/api/designs", create.formatted("dragon", RELIEF.formatted(photo, "face")), guest), HttpStatus.NOT_FOUND, "unknown_family");
        JsonNode off = assertProblem(post("/api/designs", create.formatted("lithophane", RELIEF.formatted(photo, "plate")), guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "family_not_available");
        assertThat(off.get("detail").asText()).isEqualTo("Roshni · Photo night light is not available to order right now.");
        assertProblem(post("/api/designs", "{\"source\": \"create\", \"family_id\": \"keychain\", \"template_id\": \"fridge_magnet\"}", guest),
                HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/designs", "{\"source\": \"create\", \"family_id\": \"keychain\", \"template_id\": \"nope_tag\"}", guest),
                HttpStatus.NOT_FOUND, "not_found");
        assertProblem(post("/api/designs", "{\"source\": \"upload\", \"family_id\": \"keychain\", \"features\": [" + RELIEF.formatted(photo, "face") + "]}", guest),
                HttpStatus.BAD_REQUEST, "validation_failed");

        // content the keychain cannot carry, said in the customer's words
        JsonNode form = assertProblem(post("/api/designs", create.formatted("keychain",
                "{\"type\": \"hero_mesh\", \"source\": {\"upload_id\": \"" + model + "\"}, \"anchor\": \"face\"}"), guest), HttpStatus.UNPROCESSABLE_ENTITY,
                "unsupported_feature");
        assertThat(form.get("detail").asText()).isEqualTo("Saathi keychain tag can't carry your own 3D form (Roop) yet");
        assertProblem(post("/api/designs", create.formatted("keychain", "{\"type\": \"emboss_text\", \"text\": \"Asha\", \"anchor\": \"back\"}"), guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_feature"); // lettering arrives with PR 3b
        JsonNode deep = assertProblem(post("/api/designs", create.formatted("keychain",
                "{\"type\": \"relief_image\", \"source\": {\"upload_id\": \"" + photo + "\"}, \"anchor\": \"face\", \"relief_mm\": 1.6}"), guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "param_out_of_range");
        assertThat(deep.get("params")).extracting(JsonNode::asText).containsExactly("features[0].relief_mm");
        assertThat(deep.get("detail").asText()).isEqualTo("The photo relief (Chhavi) on the Face can be at most 1.5 mm deep; 1.6 mm was asked");
        assertProblem(post("/api/designs", create.formatted("keychain", RELIEF.formatted(photo, "face") + ", " + RELIEF.formatted(photo, "face")), guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertProblem(post("/api/designs", create.formatted("keychain", RELIEF.formatted(stranger, "face")), guest), HttpStatus.NOT_FOUND, "not_found");
        JsonNode wrongKind = assertProblem(post("/api/designs", create.formatted("keychain", RELIEF.formatted(model, "face")), guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_format");
        assertThat(wrongKind.get("detail").asText()).startsWith("A photo relief (Chhavi) needs a photo");
        assertProblem(post("/api/designs", create.formatted("keychain", (RELIEF.formatted(photo, "face") + ", ").repeat(8) + RELIEF.formatted(photo, "back")),
                guest), HttpStatus.BAD_REQUEST, "validation_failed");

        // the family's default template and first allowed finish; a prompt alongside a family becomes the working title
        ResponseEntity<String> plain = post("/api/designs", "{\"source\": \"create\", \"family_id\": \"keychain\", \"prompt\": \"a keychain of my dog Bruno\", "
                + "\"features\": [" + RELIEF.formatted(photo, "face") + "]}", guest);
        assertThat(plain.getStatusCode()).as(plain.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode started = body(get("/api/designs/" + body(plain).get("design_id").asText()));
        assertThat(started.get("title").asText()).isEqualTo("a keychain of my dog Bruno");
        assertThat(started.get("latest_version").get("spec").get("template").asText()).isEqualTo("keychain_tag@1");
        assertThat(started.get("latest_version").get("spec").get("material").asText()).isEqualTo("basic_white");
        assertThat(started.get("latest_version").get("hardware").get(0).get("sku").asText()).as("known before the build finishes").isEqualTo("split_ring_25");
        assertProblem(post("/api/designs", "{\"source\": \"create\", \"prompt\": \"a keychain of my dog\"}", guest), HttpStatus.UNPROCESSABLE_ENTITY,
                "not_yet_available");

        // the family's material rules, as the portal may set them
        String rules = jdbc.queryForObject("select material_rules::text from template_families where id = 'keychain'", String.class);
        try {
            jdbc.update("update template_families set material_rules = ?::jsonb where id = 'keychain'",
                    "{\"heat_safe_only\": false, \"allowed\": null, \"excluded_finish_classes\": [\"silk\"]}");
            JsonNode silk = assertProblem(post("/api/designs", "{\"source\": \"create\", \"family_id\": \"keychain\", \"material\": \"terracotta_silk\"}", guest),
                    HttpStatus.UNPROCESSABLE_ENTITY, "unknown_material");
            assertThat(silk.get("detail").asText()).isEqualTo("Saathi · Keychain & bag charm isn't offered in silk finishes; choose another finish.");
            jdbc.update("update template_families set material_rules = ?::jsonb where id = 'keychain'",
                    "{\"heat_safe_only\": false, \"allowed\": [\"terracotta_matte\"], \"excluded_finish_classes\": []}");
            JsonNode only = assertProblem(post("/api/designs", "{\"source\": \"create\", \"family_id\": \"keychain\", \"material\": \"indigo_matte\"}", guest),
                    HttpStatus.UNPROCESSABLE_ENTITY, "unknown_material");
            assertThat(only.get("detail").asText()).isEqualTo("Saathi · Keychain & bag charm is made in Terracotta Matte only; Indigo Matte isn't offered for it.");
            ResponseEntity<String> defaulted = post("/api/designs", "{\"source\": \"create\", \"family_id\": \"keychain\"}", guest);
            assertThat(defaulted.getStatusCode()).as(defaulted.getBody()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(body(get("/api/designs/" + body(defaulted).get("design_id").asText())).get("latest_version").get("spec").get("material").asText())
                    .isEqualTo("terracotta_matte");
        } finally {
            jdbc.update("update template_families set material_rules = ?::jsonb where id = 'keychain'", rules);
        }
    }

    /** Waits for the accepted job and returns the version it made. */
    private JsonNode ready(JsonNode accepted) {
        awaitJob(accepted.get("job_id").asText(), "succeeded");
        JsonNode versions = body(get("/api/designs/" + accepted.get("design_id").asText() + "/versions"));
        for (JsonNode version : versions) {
            if (version.get("version_no").asInt() == accepted.get("version_no").asInt()) {
                return version;
            }
        }
        throw new AssertionError("version " + accepted.get("version_no") + " missing from " + versions);
    }

    private String signInAs(String phone, UUID guestId) {
        JsonNode otp = body(post("/api/auth/otp/request", "{\"phone\": \"" + phone + "\"}"));
        ResponseEntity<String> verified = post("/api/auth/otp/verify",
                "{\"request_id\": \"" + otp.get("request_id").asText() + "\", \"code\": \"" + otp.get("dev_code").asText() + "\"}", guest(guestId));
        assertThat(verified.getStatusCode()).as(verified.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(body(verified).get("attached").get("designs").asInt()).isEqualTo(1);
        return body(verified).get("access_token").asText();
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }

    private static void validateAgainst(String schema, JsonNode document) {
        Path file = Contracts.contracts(schema);
        if (Files.exists(file)) {
            assertThat(Contracts.validate(file, document)).as("%s violations", schema).isEmpty();
        }
    }
}
