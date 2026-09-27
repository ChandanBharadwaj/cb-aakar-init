package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.Contracts;

/**
 * Shelves and outcome families (Avatars) on the storefront: the Create picker lists only families that are available
 * and backed by a live template, the detail carries hardware names and the live descriptors (with the new anchor and
 * hardware fields round-tripping from the geometry stub), and Shop items name a real shelf and family.
 */
class CatalogFamiliesIntegrationTest extends AbstractIntegrationTest {

    static final String OWNER_LOGIN = "{\"email\": \"studio@aakar.local\", \"password\": \"aakar-studio\"}";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void shelvesComeInDisplayOrder() {
        ResponseEntity<String> response = get("/api/catalog/shelves");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode shelves = body(response);
        assertThat(ids(shelves)).containsExactly("home_decor", "nameplates", "kitchen", "desk_tech", "gifting", "keychains_charms");
        assertThat(shelves.get(0).get("label").asText()).isEqualTo("Home & decor");
        assertThat(shelves.get(0).get("sort_order").asInt()).isEqualTo(10);
        assertThat(shelves.get(5).get("label").asText()).isEqualTo("Keychains & charms");

        // the portal reads the same list behind a staff token
        assertProblem(get("/admin/api/catalog/shelves"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertThat(ids(body(get("/admin/api/catalog/shelves", owner())))).hasSize(6).startsWith("home_decor");
    }

    @Test
    void storefrontListsOnlyAvailableFamiliesWithALiveTemplate() {
        ResponseEntity<String> response = get("/api/families");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode families = body(response);
        // the four planar carriers (keychain_tag, fridge_magnet, hanging_ornament, desk_nameplate), phone_stand (jharokha_phone_stand@1)
        // and raw_print (raw_print@1) have live templates in the stub; lithophane is switched off and may not appear.
        assertThat(ids(families)).containsExactly("keychain", "fridge_magnet", "ornament", "nameplate", "phone_stand", "raw_print");
        assertThat(families).allSatisfy(f -> {
            assertThat(f.get("available").asBoolean()).as("%s available", f.get("id")).isTrue();
            assertThat(f.get("ready").asBoolean()).as("%s ready", f.get("id")).isTrue();
            assertThat(f.get("templates")).as("%s templates", f.get("id")).isNotEmpty();
            assertThat(f.get("template_ids")).as("%s template_ids", f.get("id")).hasSize(f.get("templates").size());
        });

        assertThat(ids(body(get("/api/families?kind=raw")))).containsExactly("raw_print");
        assertThat(ids(body(get("/api/families?kind=carrier")))).containsExactly("keychain", "fridge_magnet", "ornament", "nameplate");
        assertThat(ids(body(get("/api/families?kind=object")))).containsExactly("phone_stand");
        assertThat(ids(body(get("/api/families?kind=")))).hasSize(6);
        assertProblem(get("/api/families?kind=gadget"), HttpStatus.BAD_REQUEST, "validation_failed");

        // Every family row is a template-family.v1.json family once the read-only state is stripped, and every
        // template a template-descriptor.v1.json descriptor.
        ObjectNode document = json.createObjectNode();
        document.set("shelves", body(get("/api/catalog/shelves")));
        document.set("hardware_items", json.createArrayNode());
        ArrayNode rows = document.putArray("families");
        families.forEach(f -> rows.add(asSeedRow(f)));
        validateAgainst("schemas/template-family.v1.json", document);
        families.forEach(f -> f.get("templates").forEach(t -> validateAgainst("schemas/template-descriptor.v1.json", t)));
    }

    @Test
    void familyDetailCarriesCopyHardwareNamesAndLiveTemplates() {
        JsonNode keychain = body(get("/api/families/keychain"));
        assertThat(keychain.get("codename").asText()).isEqualTo("Saathi");
        assertThat(keychain.get("name").asText()).isEqualTo("Keychain & bag charm");
        assertThat(keychain.get("tagline").asText()).isEqualTo("Your idea, on your keys");
        assertThat(keychain.get("kind").asText()).isEqualTo("carrier");
        assertThat(keychain.get("tier").asText()).isEqualTo("launch");
        assertThat(keychain.get("shelf").asText()).isEqualTo("keychains_charms");
        assertThat(keychain.get("demand_rank").asInt()).isEqualTo(1);
        assertThat(keychain.get("default_template_id").asText()).isEqualTo("keychain_tag");
        assertThat(keychain.get("environment").asText()).isEqualTo("studio");
        assertThat(keychain.get("size_envelope_mm").get("min_longest_mm").asDouble()).isEqualTo(30.0);
        assertThat(keychain.get("size_envelope_mm").get("max_longest_mm").asDouble()).isEqualTo(60.0);
        assertThat(keychain.get("hardware")).hasSize(1);
        assertThat(keychain.get("hardware").get(0).get("sku").asText()).isEqualTo("split_ring_25");
        assertThat(keychain.get("hardware").get(0).get("qty").asInt()).isEqualTo(1);
        assertThat(keychain.get("hardware").get(0).get("name").asText()).isEqualTo("Steel split ring 25 mm");
        assertThat(keychain.get("material_rules").get("heat_safe_only").asBoolean()).isFalse();
        assertThat(keychain.get("material_rules").get("allowed").isNull()).isTrue();
        assertThat(keychain.get("material_rules").get("excluded_finish_classes")).isEmpty();
        assertThat(keychain.get("shape_tolerance").asText()).isEqualTo("any");
        assertThat(keychain.get("content_slot").get("accepts")).extracting(JsonNode::asText).containsExactly("relief_image", "emboss_text", "motif");
        assertThat(keychain.get("content_slot").get("anchors")).extracting(JsonNode::asText).containsExactly("face", "back");
        assertThat(keychain.get("content_slot").get("hero_volume").asBoolean()).isFalse();
        assertThat(keychain.get("content_slot").get("max_text_chars").asInt()).isEqualTo(16);
        assertThat(keychain.get("available").asBoolean()).isTrue();
        assertThat(keychain.get("sort_order").asInt()).isEqualTo(10);
        assertThat(keychain.get("ready").asBoolean()).isTrue();
        // price_from_paise is the family minimum of the active policy (a floor): ₹249 for keychains, none for the phone stand
        assertThat(keychain.get("price_from_paise").asLong()).isEqualTo(24_900);
        assertThat(keychain.get("template_ids")).extracting(JsonNode::asText).containsExactly("keychain_tag");

        // the live descriptor comes along, with the anchor and hardware fields the geometry service publishes
        assertThat(keychain.get("templates")).hasSize(1);
        JsonNode template = keychain.get("templates").get(0);
        assertThat(template.get("id").asText()).isEqualTo("keychain_tag");
        assertThat(template.get("family").asText()).isEqualTo("keychain");
        // photos only until lettering and motifs land (PR 3b)
        assertThat(template.get("features_supported")).extracting(JsonNode::asText).containsExactly("relief_image");
        assertThat(template.get("hardware").get(0).get("sku").asText()).isEqualTo("split_ring_25");
        assertThat(template.get("hardware").get(0).get("qty").asInt()).isEqualTo(1);
        assertThat(template.get("min_feature_mm").asDouble()).isEqualTo(0.8);
        JsonNode face = template.get("anchors").get(0);
        assertThat(face.get("id").asText()).isEqualTo("face");
        assertThat(face.get("kind").asText()).isEqualTo("surface");
        assertThat(face.get("size_mm")).extracting(JsonNode::asDouble).containsExactly(32.0, 23.5);
        assertThat(face.get("bleed_mm").asDouble()).isEqualTo(1.0);
        assertThat(face.get("accepts")).extracting(JsonNode::asText).containsExactly("relief_image");
        assertThat(face.get("max_relief_mm").asDouble()).isEqualTo(1.5);
        assertThat(face.has("max_text_height_mm")).isFalse();
        assertThat(face.has("bounds_mm")).isFalse();
        validateAgainst("schemas/template-descriptor.v1.json", template);

        // the raw family (Swaroop) carries a volume anchor
        JsonNode raw = body(get("/api/families/raw_print"));
        assertThat(raw.get("codename").asText()).isEqualTo("Swaroop");
        assertThat(raw.get("kind").asText()).isEqualTo("raw");
        assertThat(raw.get("hardware")).isEmpty();
        assertThat(raw.get("content_slot").get("hero_volume").asBoolean()).isTrue();
        assertThat(raw.get("content_slot").has("max_text_chars")).isFalse();
        JsonNode body = raw.get("templates").get(0).get("anchors").get(0);
        assertThat(body.get("id").asText()).isEqualTo("body");
        assertThat(body.get("kind").asText()).isEqualTo("volume");
        assertThat(body.get("bounds_mm")).extracting(JsonNode::asDouble).containsExactly(240.0, 240.0, 240.0);
        assertThat(body.get("accepts")).extracting(JsonNode::asText).containsExactly("hero_mesh");
        assertThat(raw.get("templates").get(0).get("features_supported")).extracting(JsonNode::asText).containsExactly("hero_mesh");

        // families that are not orderable still resolve by id, flagged, so a deep link can say "coming soon"
        JsonNode lithophane = body(get("/api/families/lithophane"));
        assertThat(lithophane.get("available").asBoolean()).isFalse();
        assertThat(lithophane.get("ready").asBoolean()).isFalse();
        assertThat(lithophane.get("templates")).isEmpty();
        assertThat(lithophane.get("material_rules").get("allowed")).extracting(JsonNode::asText).containsExactly("basic_white");
        assertThat(lithophane.get("hardware").get(0).get("name").asText()).isEqualTo("USB LED puck base 70 mm");
        JsonNode figurine = body(get("/api/families/figurine_base"));
        assertThat(figurine.get("available").asBoolean()).isFalse();
        assertThat(figurine.get("ready").asBoolean()).isFalse();
        assertThat(figurine.get("template_ids")).isEmpty();
        // the magnet is orderable now; its descriptor names one magnet, the geometry result names as many as the params ask for
        JsonNode magnet = body(get("/api/families/fridge_magnet"));
        assertThat(magnet.get("ready").asBoolean()).isTrue();
        assertThat(magnet.get("template_ids")).extracting(JsonNode::asText).containsExactly("fridge_magnet");
        assertThat(magnet.get("price_from_paise").asLong()).isEqualTo(29_900);
        assertThat(body(get("/api/families/phone_stand")).has("price_from_paise")).isFalse();
        assertThat(raw.get("price_from_paise").asLong()).isEqualTo(34_900);

        assertProblem(get("/api/families/nope"), HttpStatus.NOT_FOUND, "unknown_family");

        // the old fixture still loads beside the new descriptors, and old anchors stay as they were
        JsonNode templates = body(get("/api/templates"));
        assertThat(ids(templates)).containsExactly("jharokha_phone_stand", "keychain_tag", "fridge_magnet", "hanging_ornament", "desk_nameplate",
                "raw_print");
        JsonNode jharokhaAnchor = templates.get(0).get("anchors").get(0);
        assertThat(jharokhaAnchor.get("id").asText()).isEqualTo("side_left");
        assertThat(jharokhaAnchor.has("kind")).isFalse();
        assertThat(jharokhaAnchor.has("size_mm")).isFalse();
        assertThat(templates.get(0).get("hardware")).isEmpty();
        assertThat(templates.get(0).has("min_feature_mm")).isFalse();
    }

    @Test
    void shopItemsNameAShelfAndAFamily() {
        JsonNode stand = body(get("/api/catalog/items/jharokha-phone-stand"));
        assertThat(stand.get("category").asText()).isEqualTo("desk_tech");
        assertThat(stand.get("family_id").asText()).isEqualTo("phone_stand");
        assertThat(body(get("/api/catalog/items"))).allSatisfy(i -> assertThat(i.get("family_id").isTextual()).as("%s family_id", i.get("slug")).isTrue());
        // the family id is searchable too: "headphone_stand" is in no slug, name or description, only in family_id
        assertThat(body(get("/api/catalog/items?q=headphone_stand")).findValuesAsText("slug")).containsExactly("pillar-headphone-stand");

        String[] owner = owner();
        String slug = "test-shelf-" + UUID.randomUUID().toString().substring(0, 8);
        String item = """
                {"slug": "%s", "name": "Test shelf check", "category": "%s", %s "description": "A test item.", "template_id": "jharokha_phone_stand",
                 "default_params": {}, "default_material": "basic_white", "base_price_paise": 9900, "specs_line": "test", "environment": "studio",
                 "available": false, "media": []}
                """;
        try {
            JsonNode problem = assertProblem(post("/admin/api/catalog/items", item.formatted(slug, "not_a_shelf", ""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(problem.get("detail").asText()).contains("not_a_shelf").contains("home_decor").contains("keychains_charms");
            assertProblem(post("/admin/api/catalog/items", item.formatted(slug, "gifting", "\"family_id\": \"nope\","), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "unknown_family");
            assertThat(body(get("/admin/api/catalog/items", owner))).hasSize(6); // nothing slipped through

            ResponseEntity<String> created = post("/admin/api/catalog/items", item.formatted(slug, "gifting", "\"family_id\": \"keychain\","), owner);
            assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
            assertThat(body(created).get("category").asText()).isEqualTo("gifting");
            assertThat(body(created).get("family_id").asText()).isEqualTo("keychain");
            assertThat(body(get("/api/catalog/items/" + slug)).get("family_id").asText()).isEqualTo("keychain");
            // family_id is optional: null clears it
            JsonNode cleared = body(put("/admin/api/catalog/items/" + slug, item.formatted(slug, "gifting", "\"family_id\": null,"), owner));
            assertThat(cleared.get("family_id").isNull()).isTrue();
        } finally {
            jdbc.update("delete from catalog_items where slug = ?", slug);
        }
    }

    private String[] owner() {
        return bearer(body(post("/admin/api/auth/login", OWNER_LOGIN)).get("access_token").asText());
    }

    /** Top-level {@code id} of every element (never the nested anchor or template ids). */
    private static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    /** A storefront family minus its read-only state: what {@code template-family.v1.json#/$defs/family} describes. */
    private static JsonNode asSeedRow(JsonNode family) {
        ObjectNode row = family.deepCopy();
        row.remove(List.of("ready", "templates", "template_ids", "price_from_paise", "updated_at"));
        row.withArray("hardware").forEach(h -> ((ObjectNode) h).remove("name"));
        return row;
    }

    private static void validateAgainst(String schema, JsonNode document) {
        Path file = Contracts.contracts(schema);
        if (Files.exists(file)) {
            assertThat(Contracts.validate(file, document)).as("%s violations", schema).isEmpty();
        }
    }
}
