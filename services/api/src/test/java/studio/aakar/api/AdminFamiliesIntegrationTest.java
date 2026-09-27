package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.aakar.api.admin.StaffAccounts;
import studio.aakar.api.admin.StaffRole;
import studio.aakar.api.support.AbstractIntegrationTest;

/**
 * The portal side of outcome families (Avatars) and bought-in hardware: owner-only, audited create and update with
 * the catalog's referential checks, the pricing preview's {@code family_id}, and the read-only {@code studio} role.
 */
class AdminFamiliesIntegrationTest extends AbstractIntegrationTest {

    static final String OWNER_LOGIN = "{\"email\": \"studio@aakar.local\", \"password\": \"aakar-studio\"}";
    static final String POLICY = """
            {"machine_rate_paise_per_hour": 20000, "finishing_fee_paise": {"matte": 8000, "silk": 12000}, "packaging_fee_paise": 0,
             "margin_pct": 0, "round_to_rupees_ending_in": 9, "shipping_flat_paise": 7900, "free_shipping_above_paise": 99900,
             "shipping_label": "Shipping · Delhivery, 4 days", "hardware_markup_pct": 30,
             "family_rules": {"keychain": {"minimum_subtotal_paise": 24900, "qty_breaks": [{"min_qty": 4, "discount_pct": 10}]}}}
            """;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffAccounts staffAccounts;

    @Test
    void ownersManageHardwareAndFamiliesAndTheStudioRoleIsReadOnly() {
        String[] owner = bearer(body(post("/admin/api/auth/login", OWNER_LOGIN)).get("access_token").asText());
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String sku = "test_hook_" + suffix;
        String familyId = "test_avatar_" + suffix;
        String hardwareBody = """
                {"sku": "%s", "name": "Test brass hook 20 mm", "unit_cost_paise": 1200, "weight_g": 3.5, "supplier": "test bench", "notes": "test only"}
                """;
        String familyBody = """
                {"id": "%s", "codename": "Parikshan", "name": "Test avatar", "tagline": "For the test suite",
                 "description": "A plate that exists only in tests.", "kind": "carrier", "tier": "later", "shelf": "gifting", "demand_rank": 20,
                 "default_template_id": "test_plate", "environment": "studio",
                 "size_envelope_mm": {"min_longest_mm": 30, "max_longest_mm": 80},
                 "hardware": [{"sku": "%s", "qty": 2}],
                 "material_rules": {"heat_safe_only": false, "allowed": null, "excluded_finish_classes": ["silk"]},
                 "shape_tolerance": "any",
                 "content_slot": {"accepts": ["emboss_text", "motif"], "anchors": ["face"], "hero_volume": false, "max_text_chars": 12},
                 "available": false, "sort_order": 500}
                """;
        try {
            // hardware: the seven seeded SKUs, then create, duplicate, invalid, update, unknown, mismatch
            JsonNode hardware = body(get("/admin/api/hardware", owner));
            assertThat(hardware.findValuesAsText("sku")).contains("split_ring_25", "magnet_d10x3", "cord_200", "led_base_usb", "acrylic_4x6",
                    "nameplate_screws", "adhesive_pads");
            JsonNode ring = find(hardware, "sku", "split_ring_25");
            assertThat(ring.get("name").asText()).isEqualTo("Steel split ring 25 mm");
            assertThat(ring.get("unit_cost_paise").asLong()).isEqualTo(300);
            assertThat(ring.get("weight_g").asDouble()).isEqualTo(2.0);
            assertThat(ring.get("available").asBoolean()).isTrue();
            assertThat(ring.get("updated_at").asText()).endsWith("Z");
            ResponseEntity<String> createdHardware = post("/admin/api/hardware", hardwareBody.formatted(sku), owner);
            assertThat(createdHardware.getStatusCode()).as(createdHardware.getBody()).isEqualTo(HttpStatus.CREATED);
            JsonNode hook = body(createdHardware);
            assertThat(hook.get("sku").asText()).isEqualTo(sku);
            assertThat(hook.get("name").asText()).isEqualTo("Test brass hook 20 mm");
            assertThat(hook.get("unit_cost_paise").asLong()).isEqualTo(1200);
            assertThat(hook.get("weight_g").asDouble()).isEqualTo(3.5);
            assertThat(hook.get("available").asBoolean()).isTrue(); // default
            assertThat(hook.has("url")).isFalse();
            assertProblem(post("/admin/api/hardware", hardwareBody.formatted(sku), owner), HttpStatus.CONFLICT, "hardware_exists");
            assertProblem(post("/admin/api/hardware", "{\"sku\": \"Bad Sku\", \"name\": \"x\", \"unit_cost_paise\": -1}", owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            JsonNode updatedHardware = body(put("/admin/api/hardware/" + sku, """
                    {"sku": "%s", "name": "Test brass hook 20 mm, plated", "unit_cost_paise": 1500, "weight_g": 3.5, "available": false}
                    """.formatted(sku), owner));
            assertThat(updatedHardware.get("name").asText()).isEqualTo("Test brass hook 20 mm, plated");
            assertThat(updatedHardware.get("unit_cost_paise").asLong()).isEqualTo(1500);
            assertThat(updatedHardware.get("available").asBoolean()).isFalse();
            assertThat(updatedHardware.has("supplier")).isFalse();
            assertProblem(put("/admin/api/hardware/nope_hw", hardwareBody.formatted("nope_hw"), owner), HttpStatus.NOT_FOUND, "not_found");
            assertProblem(put("/admin/api/hardware/" + sku, hardwareBody.formatted("other_sku"), owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");

            // families: the 25 seeded rows with readiness from the geometry stub
            JsonNode all = body(get("/admin/api/families", owner));
            assertThat(all).hasSize(25);
            JsonNode keychain = find(all, "id", "keychain");
            assertThat(keychain.get("ready").asBoolean()).isTrue();
            assertThat(keychain.get("template_ids")).extracting(JsonNode::asText).containsExactly("keychain_tag");
            assertThat(keychain.get("templates")).as("the portal list names templates, it does not embed descriptors").isEmpty();
            assertThat(keychain.get("updated_at").asText()).endsWith("Z");
            JsonNode lithophane = find(all, "id", "lithophane");
            assertThat(lithophane.get("available").asBoolean()).isFalse();
            assertThat(lithophane.get("ready").asBoolean()).isFalse();
            assertThat(lithophane.get("template_ids")).isEmpty();
            assertThat(find(all, "id", "raw_print").get("ready").asBoolean()).isTrue();

            // the viewer backdrop must be one the storefront can render
            assertProblem(post("/admin/api/families", familyBody.formatted(familyId, sku).replace("\"environment\": \"studio\"", "\"environment\": \"moon_base\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");

            // create: referenced hardware is named back, the family is listed in the portal but not on the storefront
            ResponseEntity<String> createdFamily = post("/admin/api/families", familyBody.formatted(familyId, sku), owner);
            assertThat(createdFamily.getStatusCode()).as(createdFamily.getBody()).isEqualTo(HttpStatus.CREATED);
            JsonNode family = body(createdFamily);
            assertThat(family.get("id").asText()).isEqualTo(familyId);
            assertThat(family.get("codename").asText()).isEqualTo("Parikshan");
            assertThat(family.get("ready").asBoolean()).isFalse();
            assertThat(family.get("template_ids")).isEmpty();
            assertThat(family.get("templates")).isEmpty();
            assertThat(family.get("hardware").get(0).get("sku").asText()).isEqualTo(sku);
            assertThat(family.get("hardware").get(0).get("qty").asInt()).isEqualTo(2);
            assertThat(family.get("hardware").get(0).get("name").asText()).isEqualTo("Test brass hook 20 mm, plated");
            assertThat(family.get("material_rules").get("excluded_finish_classes")).extracting(JsonNode::asText).containsExactly("silk");
            assertThat(family.get("size_envelope_mm").get("max_longest_mm").asDouble()).isEqualTo(80.0);
            assertThat(family.get("content_slot").get("max_text_chars").asInt()).isEqualTo(12);
            assertThat(family.get("available").asBoolean()).isFalse();
            assertThat(family.get("sort_order").asInt()).isEqualTo(500);
            assertThat(body(get("/admin/api/families", owner))).hasSize(26);
            assertThat(topLevelIds(body(get("/api/families")))).doesNotContain(familyId);
            assertThat(body(get("/api/families/" + familyId)).get("available").asBoolean()).isFalse();

            // the catalog's referential checks
            assertProblem(post("/admin/api/families", familyBody.formatted(familyId, sku), owner), HttpStatus.CONFLICT, "family_exists");
            JsonNode shelfProblem = assertProblem(post("/admin/api/families",
                    familyBody.formatted(familyId + "b", sku).replace("\"shelf\": \"gifting\"", "\"shelf\": \"attic\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(shelfProblem.get("detail").asText()).contains("attic").contains("gifting");
            assertProblem(post("/admin/api/families", familyBody.formatted(familyId + "b", "nope_hw"), owner), HttpStatus.UNPROCESSABLE_ENTITY, "unknown_hardware");
            assertProblem(post("/admin/api/families",
                    familyBody.formatted(familyId + "b", sku).replace("\"min_longest_mm\": 30", "\"min_longest_mm\": 300"), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/families", familyBody.formatted(familyId + "b", sku).replace("\"emboss_text\"", "\"hologram\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/families",
                    familyBody.formatted(familyId + "b", sku).replace("\"allowed\": null", "\"allowed\": [\"unobtainium\"]"), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "unknown_material");
            assertProblem(post("/admin/api/families", familyBody.formatted(familyId + "b", sku).replace("\"tier\": \"later\"", "\"tier\": \"someday\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/families", "{\"id\": \"x\"}", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(body(get("/admin/api/families", owner))).hasSize(26); // nothing slipped through

            // update: the path id wins over the body id; switching a family on is not enough for the storefront without a live template
            JsonNode updated = body(put("/admin/api/families/" + familyId, familyBody.formatted("ignored_body_id", sku)
                    .replace("\"available\": false", "\"available\": true").replace("For the test suite", "Now switched on"), owner));
            assertThat(updated.get("id").asText()).isEqualTo(familyId);
            assertThat(updated.get("tagline").asText()).isEqualTo("Now switched on");
            assertThat(updated.get("available").asBoolean()).isTrue();
            assertThat(updated.get("ready").asBoolean()).isFalse();
            assertThat(topLevelIds(body(get("/api/families")))).doesNotContain(familyId);
            assertThat(body(get("/api/families/" + familyId)).get("tagline").asText()).isEqualTo("Now switched on");
            assertProblem(put("/admin/api/families/nope_family", familyBody.formatted("nope_family", sku), owner), HttpStatus.NOT_FOUND, "unknown_family");

            // pricing: the preview prices a piece of a seeded family (its default hardware at the draft's markup, setup fee and
            // minimum) and refuses an unknown one; a policy may only carry rules for seeded families; the seeded policy exposes the
            // new fields without zero noise
            String preview = "{\"policy\": " + POLICY + ", \"material\": \"terracotta_silk\", \"extruded_volume_cm3\": 51.6, \"print_seconds\": 13200, \"family_id\": \"%s\"}";
            JsonNode priced = body(post("/admin/api/pricing/preview", preview.formatted("keychain"), owner));
            // The contracts example (₹296 material + ₹733 print time + ₹120 finishing = ₹1,149) now also carries the keychain's split
            // ring: ₹3 × 1.30 markup = ₹3.90 → ₹4, so ₹1,153 → ₹1,159. The keychain minimum (₹249) is below that and does not lift it;
            // keychains have no setup fee. Before PR 5 the family was only checked for existence and the total was ₹1,149.
            assertThat(priced.get("total_paise").asLong()).isEqualTo(115_900);
            assertThat(priced.get("family_id").asText()).isEqualTo("keychain");
            assertThat(priced.get("lines").findValuesAsText("code")).containsExactly("material", "machine_time", "finishing", "hardware");
            assertThat(priced.get("lines").get(3).get("label").asText()).isEqualTo("Steel split ring 25 mm · 1");
            assertThat(priced.get("lines").get(3).get("amount_paise").asLong()).isEqualTo(400);
            assertThat(priced.has("minimum_subtotal_paise")).isFalse();
            // a small piece is lifted to the family minimum, and the raw family adds its setup line
            JsonNode small = body(post("/admin/api/pricing/preview", ("{\"policy\": " + POLICY + ", \"material\": \"basic_white\", "
                    + "\"extruded_volume_cm3\": 10, \"print_seconds\": 1800, \"family_id\": \"keychain\"}"), owner));
            assertThat(small.get("subtotal_paise").asLong()).isEqualTo(24_900); // ₹47 + ₹100 + ₹80 + ₹4 = ₹231 → ₹239 → minimum ₹249
            assertThat(small.get("minimum_subtotal_paise").asLong()).isEqualTo(24_900);
            JsonNode raw = body(post("/admin/api/pricing/preview", ("{\"policy\": " + POLICY.replace("\"keychain\": {", "\"raw_print\": {\"setup_fee_paise\": 9900}, \"keychain\": {")
                    + ", \"material\": \"basic_white\", \"extruded_volume_cm3\": 20, \"print_seconds\": 5400, \"family_id\": \"raw_print\"}"), owner));
            assertThat(raw.get("lines").findValuesAsText("code")).containsExactly("material", "machine_time", "finishing", "setup");
            assertThat(raw.get("subtotal_paise").asLong()).isEqualTo(57_900); // ₹94 + ₹300 + ₹80 + ₹99 setup = ₹573 → ₹579
            assertThat(priced.get("policy_version").asText()).isEqualTo("preview");
            assertProblem(post("/admin/api/pricing/preview", preview.formatted("nope"), owner), HttpStatus.UNPROCESSABLE_ENTITY, "unknown_family");
            assertProblem(post("/admin/api/pricing/policies",
                    "{\"version\": \"test-bad-family-" + suffix + "\", \"policy\": " + POLICY.replace("\"keychain\"", "\"nope\"") + "}", owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "unknown_family");
            assertProblem(post("/admin/api/pricing/policies",
                    "{\"version\": \"test-bad-markup-" + suffix + "\", \"policy\": " + POLICY.replace("\"hardware_markup_pct\": 30", "\"hardware_markup_pct\": 900") + "}", owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            JsonNode carriers = find(body(get("/admin/api/pricing/policies", owner)), "version", "2026-10-carriers");
            assertThat(carriers.get("policy").get("hardware_markup_pct").asDouble()).isEqualTo(30.0);
            assertThat(carriers.get("policy").get("family_rules").get("raw_print").get("setup_fee_paise").asLong()).isEqualTo(9900);
            assertThat(carriers.get("policy").get("family_rules").get("raw_print").get("minimum_subtotal_paise").asLong()).isEqualTo(34_900);
            assertThat(carriers.get("policy").get("family_rules").get("keychain").has("setup_fee_paise")).isFalse();
            assertThat(carriers.get("policy").get("family_rules").get("keychain").has("qty_breaks")).isFalse();
            assertThat(carriers.get("policy").has("version")).isFalse();

            // a studio-role account reads everything and writes nothing here
            String karigarEmail = "karigar-" + suffix + "@aakar.local";
            staffAccounts.create(karigarEmail, "Karigar Desk", StaffRole.studio, "aakar-karigar");
            String[] karigar = bearer(body(post("/admin/api/auth/login", "{\"email\": \"" + karigarEmail + "\", \"password\": \"aakar-karigar\"}"))
                    .get("access_token").asText());
            assertThat(get("/admin/api/families", karigar).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get("/admin/api/hardware", karigar).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get("/admin/api/catalog/shelves", karigar).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertProblem(post("/admin/api/families", familyBody.formatted("karigar_avatar", sku), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(put("/admin/api/families/" + familyId, familyBody.formatted(familyId, sku), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(post("/admin/api/hardware", hardwareBody.formatted("karigar_hook"), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(put("/admin/api/hardware/" + sku, hardwareBody.formatted(sku), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertThat(body(get("/api/families/" + familyId)).get("tagline").asText()).isEqualTo("Now switched on"); // nothing changed

            // the audit trail: before/after for the family (without the descriptor blobs) and the hardware item
            List<JsonNode> entries = body(get("/admin/api/audit?size=200", owner)).get("items").findParents("action");
            List<JsonNode> familyEntries = entries.stream().filter(e -> e.get("target").asText().equals(familyId)).toList();
            assertThat(familyEntries).extracting(e -> e.get("action").asText()).containsExactly("family.update", "family.create"); // newest first
            assertThat(familyEntries).allSatisfy(e -> assertThat(e.get("staff_email").asText()).isEqualTo("studio@aakar.local"));
            JsonNode created = familyEntries.get(1);
            assertThat(created.get("before").isNull()).isTrue();
            assertThat(created.get("after").get("codename").asText()).isEqualTo("Parikshan");
            assertThat(created.get("after").get("templates")).isEmpty();
            JsonNode changed = familyEntries.get(0);
            assertThat(changed.get("before").get("tagline").asText()).isEqualTo("For the test suite");
            assertThat(changed.get("after").get("tagline").asText()).isEqualTo("Now switched on");
            assertThat(changed.get("before").get("available").asBoolean()).isFalse();
            assertThat(changed.get("after").get("available").asBoolean()).isTrue();
            List<JsonNode> hardwareEntries = entries.stream().filter(e -> e.get("target").asText().equals(sku)).toList();
            assertThat(hardwareEntries).extracting(e -> e.get("action").asText()).containsExactly("hardware.update", "hardware.create");
            assertThat(hardwareEntries.get(1).get("after").get("unit_cost_paise").asLong()).isEqualTo(1200);
            assertThat(hardwareEntries.get(0).get("before").get("unit_cost_paise").asLong()).isEqualTo(1200);
            assertThat(hardwareEntries.get(0).get("after").get("unit_cost_paise").asLong()).isEqualTo(1500);
        } finally {
            // leave the shared database as the other test classes expect it
            jdbc.update("delete from template_families where id = ?", familyId);
            jdbc.update("delete from hardware_items where sku = ?", sku);
        }
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode node : array) {
            if (node.hasNonNull(field) && node.get(field).asText().equals(value)) {
                return node;
            }
        }
        throw new AssertionError("No element with " + field + " = " + value + " in " + array);
    }

    private static List<String> topLevelIds(JsonNode array) {
        List<String> ids = new java.util.ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }
}
