package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.aakar.api.admin.StaffAccounts;
import studio.aakar.api.admin.StaffRole;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.Contracts;

/**
 * Experiences (Duniya) and viewer environments (Mahaul), plan §7: the Shop lists the available experiences in order with
 * their orderable avatars expanded (the geometry stub publishes live templates for keychain, fridge_magnet, ornament,
 * nameplate, phone_stand and raw_print, and for the PR 8 keepsakes lithophane, figurine_base, photo_frame and keycap, which the
 * catalog keeps switched off; so the keepsakes and the avatars without a template are left out there but kept in the portal's
 * rows), slugs resolve any experience, the portal's owner-only audited CRUD with its
 * referential checks, the environments table behind every {@code environment} field, and designs that remember the
 * experience they started from.
 */
class ExperiencesIntegrationTest extends AbstractIntegrationTest {

    static final List<String> LIVE_EXPERIENCES = List.of("festive", "desk_gaming", "memories", "kids_party");
    static final List<String> ENVIRONMENTS = List.of("studio", "teak_table_candlelight", "desk_oak", "dashboard", "kitchen_marble",
            "balcony_daylight", "comic_rooftop_night");
    static final String EXPERIENCE = """
            {"id": "%s", "codename": "Parikshan", "slug": "%s", "title": "Test world", "tagline": "Only in tests",
             "description": "A theme that exists only in the test suite.", "environment": "studio",
             "surface": {"accent": "#7E9A7B", "paper_tint": "#F2EFE6", "hero_media": "/duniya/test/hero.webp"},
             "style": "modern_zen", "motif_pack": ["lotus", "paisley"],
             "avatars": ["lithophane", "keychain"], "items": ["jharokha-phone-stand"],
             "collections": [{"id": "test_originals", "title": "Test originals", "licence_ref": null}],
             "season": [{"starts_on": "2026-10-20", "ends_on": "2026-11-10", "label": "Diwali 2026"},
                        {"starts_on": "--12-20", "ends_on": "--01-05", "label": "New Year"}],
             "available": true, "sort_order": 900}
            """;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffAccounts staffAccounts;

    @Test
    void theShopListsAvailableExperiencesWithTheirOrderableAvatars() {
        ResponseEntity<String> response = get("/api/experiences");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode experiences = body(response);
        // all five, Katha (comics) included since V14 opened it with its comic backdrop
        assertThat(ids(experiences)).containsExactly("festive", "desk_gaming", "memories", "kids_party", "comics");

        JsonNode utsav = experiences.get(0);
        assertThat(utsav.get("codename").asText()).isEqualTo("Utsav");
        assertThat(utsav.get("slug").asText()).isEqualTo("utsav");
        assertThat(utsav.get("title").asText()).isEqualTo("Festive & gifting");
        assertThat(utsav.get("tagline").asText()).isNotBlank();
        assertThat(utsav.get("environment").asText()).isEqualTo("teak_table_candlelight");
        assertThat(utsav.get("style").asText()).isEqualTo("jaipur_heritage");
        assertThat(utsav.get("surface").get("accent").asText()).isEqualTo("#D8AE5B");
        assertThat(utsav.get("surface").get("paper_tint").isNull()).isTrue();
        assertThat(utsav.get("surface").get("hero_media").isNull()).isTrue();
        assertThat(utsav.get("motif_pack")).extracting(JsonNode::asText).containsExactly("star_rangoli", "lotus", "paisley");
        assertThat(utsav.get("season").findValuesAsText("label")).containsExactly("Diwali", "Christmas", "Rakhi", "Valentine's");
        assertThat(utsav.get("season").get(0).get("starts_on").asText()).isEqualTo("--10-01");
        assertThat(utsav.get("season").get(0).get("ends_on").asText()).isEqualTo("--11-30");
        assertThat(utsav.get("collections")).isEmpty();
        assertThat(utsav.get("available").asBoolean()).isTrue();
        assertThat(utsav.get("sort_order").asInt()).isEqualTo(10);

        // avatars: the orderable families in the experience's order (the second-wave photo frame and keycap stay switched off, and
        // families without a live template are left out)
        assertThat(ids(utsav.get("avatars"))).containsExactly("lithophane", "ornament", "nameplate", "keychain", "fridge_magnet");
        assertThat(ids(experiences.get(1).get("avatars"))).containsExactly("keychain", "nameplate", "phone_stand");
        assertThat(ids(experiences.get(2).get("avatars"))).containsExactly("lithophane", "figurine_base", "fridge_magnet");
        assertThat(ids(experiences.get(3).get("avatars"))).containsExactly("keychain", "fridge_magnet", "ornament");
        assertThat(ids(experiences.get(4).get("avatars"))).containsExactly("keychain", "figurine_base", "nameplate", "fridge_magnet", "lithophane");
        // in the shape of GET /api/families: copy, readiness, live templates and the family floor
        JsonNode jhoomar = utsav.get("avatars").get(1);
        JsonNode family = body(get("/api/families/ornament"));
        assertThat(jhoomar.get("codename").asText()).isEqualTo("Jhoomar");
        assertThat(jhoomar.get("name").asText()).isEqualTo(family.get("name").asText());
        assertThat(jhoomar.get("ready").asBoolean()).isTrue();
        assertThat(jhoomar.get("available").asBoolean()).isTrue();
        assertThat(jhoomar.get("template_ids")).extracting(JsonNode::asText).containsExactly("hanging_ornament");
        assertThat(jhoomar.get("templates").get(0).get("id").asText()).isEqualTo("hanging_ornament");
        assertThat(jhoomar.path("price_from_paise")).isEqualTo(family.path("price_from_paise"));
        // price_from_paise is the lowest floor among the orderable avatars (whatever the active policy says)
        experiences.forEach(x -> {
            Long lowest = floors(x.get("avatars")).stream().min(Long::compare).orElse(null);
            if (lowest == null) {
                assertThat(x.has("price_from_paise")).as("%s price_from_paise", x.get("id")).isFalse();
            } else {
                assertThat(x.get("price_from_paise").asLong()).as("%s price_from_paise", x.get("id")).isEqualTo(lowest);
            }
        });

        // items: the curated Shop items in order, with their Coming soon state
        assertThat(utsav.get("items").findValuesAsText("slug")).containsExactly("kantha-nameplate", "ajrakh-coasters");
        assertThat(utsav.get("items").get(0).get("available").asBoolean()).isFalse();
        JsonNode adda = experiences.get(1);
        assertThat(adda.get("codename").asText()).isEqualTo("Adda");
        assertThat(adda.get("items").findValuesAsText("slug")).containsExactly("jharokha-phone-stand", "pillar-headphone-stand");
        assertThat(adda.get("items").get(0).get("available").asBoolean()).isTrue();
        assertThat(adda.get("items").get(0).get("template_id").asText()).isEqualTo("jharokha_phone_stand");
        assertThat(experiences.get(2).get("items")).isEmpty();

        // every experience is an experience.v1.json row once the expansions are folded back into ids
        ObjectNode document = json.createObjectNode();
        document.set("environments", body(get("/api/environments")));
        ArrayNode rows = document.putArray("experiences");
        experiences.forEach(x -> rows.add(asRow(x)));
        validateAgainst(document);
    }

    @Test
    void aSlugResolvesAnyExperience() {
        JsonNode utsav = body(get("/api/experiences/utsav"));
        assertThat(utsav.get("id").asText()).isEqualTo("festive");
        assertThat(ids(utsav.get("avatars"))).containsExactly("lithophane", "ornament", "nameplate", "keychain", "fridge_magnet");

        // Katha, open since V14: the comic backdrop, style and motif pack, and its orderable avatars (the keycap stays switched off)
        JsonNode katha = body(get("/api/experiences/katha"));
        assertThat(katha.get("id").asText()).isEqualTo("comics");
        assertThat(katha.get("codename").asText()).isEqualTo("Katha");
        assertThat(katha.get("title").asText()).isEqualTo("Comics & heroes");
        assertThat(katha.get("available").asBoolean()).isTrue();
        assertThat(katha.get("environment").asText()).isEqualTo("comic_rooftop_night");
        assertThat(katha.get("style").asText()).isEqualTo("comic_pop");
        assertThat(katha.get("motif_pack")).extracting(JsonNode::asText).containsExactly("comic_bursts");
        assertThat(katha.get("collections")).as("no licensed collections without a licence").isEmpty();
        assertThat(ids(katha.get("avatars"))).containsExactly("keychain", "figurine_base", "nameplate", "fridge_magnet", "lithophane");

        assertProblem(get("/api/experiences/nope"), HttpStatus.NOT_FOUND, "unknown_experience");
        assertProblem(get("/api/experiences/festive"), HttpStatus.NOT_FOUND, "unknown_experience"); // slugs, not ids
    }

    @Test
    void environmentsAreTheBackdropReference() {
        JsonNode environments = body(get("/api/environments"));
        assertThat(ids(environments)).containsExactlyElementsOf(ENVIRONMENTS);
        JsonNode teak = environments.get(1);
        assertThat(teak.get("label").asText()).isEqualTo("Chettinad teak · candlelight");
        assertThat(teak.get("surface").asText()).isEqualTo("stage");
        assertThat(teak.get("preset_key").asText()).isEqualTo("teak_table_candlelight");
        assertThat(teak.get("palette")).extracting(JsonNode::asText).containsExactly("#1A1512");
        assertThat(teak.get("sort_order").asInt()).isEqualTo(20);
        JsonNode rooftop = environments.get(6);
        assertThat(rooftop.get("label").asText()).isEqualTo("Comic rooftop · night");
        assertThat(rooftop.get("palette")).hasSize(3);

        // the portal reads the same list behind a staff token
        assertProblem(get("/admin/api/environments"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertThat(body(get("/admin/api/environments", staffOwner()))).isEqualTo(environments);

        // and the served OpenAPI document lists the storefront and portal paths
        JsonNode docs = body(get("/v3/api-docs"));
        assertThat(docs.get("paths").fieldNames()).toIterable().contains("/api/experiences", "/api/experiences/{slug}", "/api/environments",
                "/admin/api/experiences", "/admin/api/experiences/{experienceId}", "/admin/api/environments");
    }

    @Test
    void ownersManageExperiencesAuditedAndTheStudioRoleIsReadOnly() {
        String[] owner = staffOwner();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String id = "test_world_" + suffix;
        String slug = "test-world-" + suffix;
        String renamed = slug + "-2";
        try {
            // the rows as stored: every avatar, orderable or not, and item slugs
            JsonNode all = body(get("/admin/api/experiences", owner));
            assertThat(ids(all)).containsExactly("festive", "desk_gaming", "memories", "kids_party", "comics");
            JsonNode festive = all.get(0);
            assertThat(festive.get("avatars")).extracting(JsonNode::asText).containsExactly("lithophane", "ornament", "nameplate", "keychain", "fridge_magnet");
            assertThat(festive.get("items")).extracting(JsonNode::asText).containsExactly("kantha-nameplate", "ajrakh-coasters");
            assertThat(festive.get("updated_at").asText()).endsWith("Z");
            JsonNode comics = all.get(4);
            assertThat(comics.get("avatars")).extracting(JsonNode::asText)
                    .containsExactly("keychain", "keycap", "figurine_base", "nameplate", "fridge_magnet", "lithophane");
            assertThat(comics.get("available").asBoolean()).isTrue();
            ObjectNode document = json.createObjectNode();
            document.set("environments", body(get("/admin/api/environments", owner)));
            ArrayNode rows = document.putArray("experiences");
            all.forEach(x -> {
                ObjectNode row = x.deepCopy();
                row.remove("updated_at");
                rows.add(row);
            });
            validateAgainst(document);

            // create: listed on the Shop at its sort order, avatars filtered to the orderable ones there but kept here
            ResponseEntity<String> created = post("/admin/api/experiences", EXPERIENCE.formatted(id, slug), owner);
            assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
            JsonNode world = body(created);
            assertThat(world.get("id").asText()).isEqualTo(id);
            assertThat(world.get("slug").asText()).isEqualTo(slug);
            assertThat(world.get("codename").asText()).isEqualTo("Parikshan");
            assertThat(world.get("avatars")).extracting(JsonNode::asText).containsExactly("lithophane", "keychain");
            assertThat(world.get("items")).extracting(JsonNode::asText).containsExactly("jharokha-phone-stand");
            assertThat(world.get("surface").get("paper_tint").asText()).isEqualTo("#F2EFE6");
            assertThat(world.get("surface").get("hero_media").asText()).isEqualTo("/duniya/test/hero.webp");
            assertThat(world.get("collections").get(0).get("id").asText()).isEqualTo("test_originals");
            assertThat(world.get("collections").get(0).get("licence_ref").isNull()).isTrue();
            assertThat(world.get("season").get(1).get("starts_on").asText()).isEqualTo("--12-20");
            assertThat(world.get("style").asText()).isEqualTo("modern_zen");
            assertThat(world.get("sort_order").asInt()).isEqualTo(900);
            assertThat(body(get("/admin/api/experiences", owner))).hasSize(6);
            JsonNode shop = body(get("/api/experiences"));
            assertThat(ids(shop)).containsExactly("festive", "desk_gaming", "memories", "kids_party", "comics", id);
            assertThat(ids(shop.get(5).get("avatars"))).containsExactly("lithophane", "keychain");
            assertThat(shop.get(5).get("items").findValuesAsText("slug")).containsExactly("jharokha-phone-stand");
            assertThat(body(get("/api/experiences/" + slug)).get("id").asText()).isEqualTo(id);


            // conflicts
            assertProblem(post("/admin/api/experiences", EXPERIENCE.formatted(id, slug + "-x"), owner), HttpStatus.CONFLICT, "experience_exists");
            assertProblem(post("/admin/api/experiences", EXPERIENCE.formatted(id + "_x", "utsav"), owner), HttpStatus.CONFLICT, "slug_exists");

            // what the catalog checks
            String other = EXPERIENCE.formatted(id + "_x", slug + "-x");
            JsonNode backdrop = assertProblem(post("/admin/api/experiences", other.replace("\"environment\": \"studio\"", "\"environment\": \"moon_base\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(backdrop.get("detail").asText()).contains("moon_base").contains("studio").contains("comic_rooftop_night");
            JsonNode avatar = assertProblem(post("/admin/api/experiences", other.replace("\"lithophane\", \"keychain\"", "\"lithophane\", \"hoverboard\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "unknown_family");
            assertThat(avatar.get("detail").asText()).contains("hoverboard");
            JsonNode item = assertProblem(post("/admin/api/experiences", other.replace("\"jharokha-phone-stand\"", "\"moon-lamp\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(item.get("detail").asText()).contains("moon-lamp");
            assertProblem(post("/admin/api/experiences", other.replace("\"lithophane\", \"keychain\"", "\"keychain\", \"keychain\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/experiences", other.replace("[\"lotus\", \"paisley\"]", "[\"lotus\", \"lotus\"]"), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            JsonNode mixed = assertProblem(post("/admin/api/experiences", other.replace("\"ends_on\": \"2026-11-10\"", "\"ends_on\": \"--11-10\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(mixed.get("detail").asText()).contains("Diwali 2026").contains("mixes a date and a month-day");
            assertProblem(post("/admin/api/experiences", other.replace("\"ends_on\": \"2026-11-10\"", "\"ends_on\": \"2026-10-10\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/experiences", other.replace("\"starts_on\": \"--12-20\"", "\"starts_on\": \"--02-30\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/experiences", other.replace("\"starts_on\": \"--12-20\"", "\"starts_on\": \"December\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/experiences", other.replace("\"modern_zen\"", "\"baroque\""), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/experiences", other.replace(slug + "-x", "Test World"), owner), HttpStatus.UNPROCESSABLE_ENTITY,
                    "validation_failed");
            assertProblem(post("/admin/api/experiences", other.replace("\"#7E9A7B\"", "\"sage\""), owner), HttpStatus.UNPROCESSABLE_ENTITY,
                    "validation_failed");
            assertProblem(post("/admin/api/experiences", "{\"id\": \"x\"}", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(body(get("/admin/api/experiences", owner))).hasSize(6); // nothing slipped through

            // update: the path id wins; a new order, a new slug and the switch off take the experience off the Shop
            JsonNode updated = body(put("/admin/api/experiences/" + id, EXPERIENCE.formatted("ignored_body_id", renamed)
                    .replace("\"lithophane\", \"keychain\"", "\"keychain\", \"lithophane\", \"ornament\"")
                    .replace("Parikshan", "Pariksha").replace("\"available\": true", "\"available\": false"), owner));
            assertThat(updated.get("id").asText()).isEqualTo(id);
            assertThat(updated.get("codename").asText()).isEqualTo("Pariksha");
            assertThat(updated.get("slug").asText()).isEqualTo(renamed);
            assertThat(updated.get("avatars")).extracting(JsonNode::asText).containsExactly("keychain", "lithophane", "ornament");
            assertThat(updated.get("available").asBoolean()).isFalse();
            assertThat(ids(body(get("/api/experiences")))).containsExactly("festive", "desk_gaming", "memories", "kids_party", "comics");
            JsonNode off = body(get("/api/experiences/" + renamed));
            assertThat(off.get("available").asBoolean()).isFalse();
            assertThat(ids(off.get("avatars"))).containsExactly("keychain", "lithophane", "ornament");
            assertProblem(get("/api/experiences/" + slug), HttpStatus.NOT_FOUND, "unknown_experience");
            assertProblem(put("/admin/api/experiences/nope_world", EXPERIENCE.formatted("nope_world", "nope-world"), owner), HttpStatus.NOT_FOUND,
                    "unknown_experience");
            assertProblem(put("/admin/api/experiences/" + id, EXPERIENCE.formatted(id, "adda"), owner), HttpStatus.CONFLICT, "slug_exists");
            // keeping its own slug is not a conflict, and style, lists and sort order fall back to their defaults
            JsonNode minimal = body(put("/admin/api/experiences/" + id, """
                    {"id": "%s", "codename": "Pariksha", "slug": "%s", "title": "Test world", "environment": "desk_oak",
                     "surface": {"accent": "#34426B"}, "avatars": [], "available": false}
                    """.formatted(id, renamed), owner));
            assertThat(minimal.get("style").asText()).isEqualTo("none");
            assertThat(minimal.get("motif_pack")).isEmpty();
            assertThat(minimal.get("avatars")).isEmpty();
            assertThat(minimal.get("items")).isEmpty();
            assertThat(minimal.get("season")).isEmpty();
            assertThat(minimal.get("sort_order").asInt()).isEqualTo(100);
            assertThat(minimal.has("tagline")).isFalse();
            assertThat(minimal.get("surface").get("paper_tint").isNull()).isTrue();

            // a studio-role account reads everything and writes nothing here
            String karigarEmail = "karigar-duniya-" + suffix + "@aakar.local";
            staffAccounts.create(karigarEmail, "Karigar Desk", StaffRole.studio, "aakar-karigar");
            String[] karigar = bearer(body(post("/admin/api/auth/login", "{\"email\": \"" + karigarEmail + "\", \"password\": \"aakar-karigar\"}"))
                    .get("access_token").asText());
            assertThat(get("/admin/api/experiences", karigar).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get("/admin/api/environments", karigar).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertProblem(post("/admin/api/experiences", EXPERIENCE.formatted("karigar_world", "karigar-world"), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(put("/admin/api/experiences/" + id, EXPERIENCE.formatted(id, renamed), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertThat(body(get("/api/experiences/" + renamed)).get("codename").asText()).isEqualTo("Pariksha"); // nothing changed

            // the audit trail: who changed what, with the rows before and after (ids, not the expanded families)
            List<JsonNode> entries = body(get("/admin/api/audit?size=200", owner)).get("items").findParents("action").stream()
                    .filter(e -> e.get("target").asText().equals(id)).toList();
            assertThat(entries).extracting(e -> e.get("action").asText()).containsExactly("experience.update", "experience.update", "experience.create");
            assertThat(entries).allSatisfy(e -> assertThat(e.get("staff_email").asText()).isEqualTo("studio@aakar.local"));
            JsonNode create = entries.get(2);
            assertThat(create.get("before").isNull()).isTrue();
            assertThat(create.get("after").get("codename").asText()).isEqualTo("Parikshan");
            assertThat(create.get("after").get("avatars")).extracting(JsonNode::asText).containsExactly("lithophane", "keychain");
            JsonNode reorder = entries.get(1);
            assertThat(reorder.get("before").get("avatars")).extracting(JsonNode::asText).containsExactly("lithophane", "keychain");
            assertThat(reorder.get("after").get("avatars")).extracting(JsonNode::asText).containsExactly("keychain", "lithophane", "ornament");
            assertThat(reorder.get("before").get("available").asBoolean()).isTrue();
            assertThat(reorder.get("after").get("available").asBoolean()).isFalse();
            assertThat(reorder.get("after").get("slug").asText()).isEqualTo(renamed);
        } finally {
            // leave the shared database as the other test classes expect it (avatars and items cascade)
            jdbc.update("delete from experiences where id like ?", "test_world_" + suffix + "%");
        }
    }

    @Test
    void familiesAndShopItemsNameABackdropFromTheTable() {
        String[] owner = staffOwner();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String familyId = "test_backdrop_" + suffix;
        String slug = "test-backdrop-" + suffix;
        String family = """
                {"id": "%s", "codename": "Parikshan", "name": "Test avatar", "kind": "carrier", "tier": "later", "shelf": "gifting",
                 "default_template_id": "test_plate", %s "shape_tolerance": "any", "content_slot": {"accepts": ["emboss_text"]}, "available": false}
                """;
        String item = """
                {"slug": "%s", "name": "Test backdrop check", "category": "gifting", "description": "A test item.", "template_id": "jharokha_phone_stand",
                 "default_params": {}, "default_material": "basic_white", "base_price_paise": 9900, "specs_line": "test", %s
                 "available": false, "media": []}
                """;
        try {
            // a backdrop is valid because the environments table has it: the comic rooftop was never in the old hard-coded list
            JsonNode problem = assertProblem(post("/admin/api/families", family.formatted(familyId, "\"environment\": \"moon_base\","), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(problem.get("detail").asText()).contains("moon_base").contains("studio").contains("comic_rooftop_night");
            ResponseEntity<String> created = post("/admin/api/families", family.formatted(familyId, "\"environment\": \"comic_rooftop_night\","), owner);
            assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
            assertThat(body(created).get("environment").asText()).isEqualTo("comic_rooftop_night");
            JsonNode studio = body(put("/admin/api/families/" + familyId, family.formatted(familyId, ""), owner));
            assertThat(studio.get("environment").asText()).as("no environment means the studio").isEqualTo("studio");

            // Shop items: the environment is optional, and when given it must be a backdrop too
            problem = assertProblem(post("/admin/api/catalog/items", item.formatted(slug, "\"environment\": \"moon_base\","), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(problem.get("detail").asText()).contains("moon_base").contains("balcony_daylight");
            ResponseEntity<String> added = post("/admin/api/catalog/items", item.formatted(slug, "\"environment\": \"comic_rooftop_night\","), owner);
            assertThat(added.getStatusCode()).as(added.getBody()).isEqualTo(HttpStatus.CREATED);
            assertThat(body(added).get("environment").asText()).isEqualTo("comic_rooftop_night");
            JsonNode cleared = body(put("/admin/api/catalog/items/" + slug, item.formatted(slug, "\"environment\": null,"), owner));
            assertThat(cleared.get("environment").isNull()).isTrue();
        } finally {
            jdbc.update("delete from template_families where id = ?", familyId);
            jdbc.update("delete from catalog_items where slug = ?", slug);
        }
    }

    @Test
    void aDesignRemembersTheExperienceItStartedFrom() {
        String[] guest = guest(UUID.randomUUID());
        ResponseEntity<String> shop = post("/api/designs",
                "{\"source\": \"shop\", \"catalog_item_slug\": \"jharokha-phone-stand\", \"experience_id\": \"desk_gaming\"}", guest);
        assertThat(shop.getStatusCode()).as(shop.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        awaitJob(body(shop).get("job_id").asText(), "succeeded");
        JsonNode design = body(get("/api/designs/" + body(shop).get("design_id").asText()));
        assertThat(design.get("status").asText()).isEqualTo("ready");
        assertThat(design.get("experience_id").asText()).isEqualTo("desk_gaming");
        assertThat(design.get("family_id").asText()).isEqualTo("phone_stand");
        assertThat(jdbc.queryForObject("select experience_id from designs where id = ?::uuid", String.class, design.get("id").asText()))
                .isEqualTo("desk_gaming");

        // the Avatar path too; an experience that is not on the Shop yet still names the theme
        ResponseEntity<String> avatar = post("/api/designs", "{\"source\": \"create\", \"family_id\": \"keychain\", \"experience_id\": \"festive\"}", guest);
        assertThat(avatar.getStatusCode()).as(avatar.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode keychain = body(get("/api/designs/" + body(avatar).get("design_id").asText()));
        assertThat(keychain.get("experience_id").asText()).isEqualTo("festive");
        assertThat(keychain.get("family_id").asText()).isEqualTo("keychain");
        ResponseEntity<String> comics = post("/api/designs", "{\"source\": \"shop\", \"catalog_item_slug\": \"jharokha-phone-stand\", "
                + "\"experience_id\": \"comics\"}", guest);
        assertThat(comics.getStatusCode()).as(comics.getBody()).isEqualTo(HttpStatus.ACCEPTED);

        JsonNode unknown = assertProblem(post("/api/designs",
                "{\"source\": \"shop\", \"catalog_item_slug\": \"jharokha-phone-stand\", \"experience_id\": \"utsav\"}", guest),
                HttpStatus.UNPROCESSABLE_ENTITY, "unknown_experience");
        assertThat(unknown.get("detail").asText()).contains("utsav");
        ResponseEntity<String> plain = post("/api/designs", "{\"source\": \"shop\", \"catalog_item_slug\": \"jharokha-phone-stand\"}", guest);
        assertThat(plain.getStatusCode()).as(plain.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(body(get("/api/designs/" + body(plain).get("design_id").asText())).get("experience_id").isNull()).isTrue();
    }

    /** Top-level {@code id} of every element (never nested ids). */
    private static List<String> ids(JsonNode array) {
        List<String> ids = new ArrayList<>();
        array.forEach(n -> ids.add(n.get("id").asText()));
        return ids;
    }

    private static List<Long> floors(JsonNode families) {
        List<Long> floors = new ArrayList<>();
        families.forEach(f -> floors.add(f.hasNonNull("price_from_paise") ? f.get("price_from_paise").asLong() : null));
        return floors.stream().filter(Objects::nonNull).toList();
    }

    /** A storefront experience folded back into an {@code experience.v1.json} row: avatars and items as ids, no floor. */
    private JsonNode asRow(JsonNode experience) {
        ObjectNode row = experience.deepCopy();
        row.remove("price_from_paise");
        ArrayNode avatars = json.createArrayNode();
        experience.get("avatars").forEach(f -> avatars.add(f.get("id").asText()));
        row.set("avatars", avatars);
        ArrayNode items = json.createArrayNode();
        experience.get("items").forEach(i -> items.add(i.get("slug").asText()));
        row.set("items", items);
        return row;
    }

    private static void validateAgainst(JsonNode document) {
        Path file = Contracts.contracts("schemas/experience.v1.json");
        if (Files.exists(file)) {
            assertThat(Contracts.validate(file, document)).as("experience.v1.json violations").isEmpty();
        }
    }
}
