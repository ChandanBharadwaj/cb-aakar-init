package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.aakar.api.admin.StaffAccounts;
import studio.aakar.api.admin.StaffRole;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.SampleFiles;

/**
 * Content rules, the trademark guardrail behind Katha (plan §8, open decision 16): the seeded terms (V13), the portal's
 * owner-only audited CRUD, a text (Naam) naming a protected hero refused with 422 {@code protected_term} at create and at every
 * params edit (case, spacing and punctuation variants included, never the term in the answer), short terms only as whole
 * words, a term switched off no longer blocking, and uploads whose file name mentions a term held for review with the term in
 * the reason. Terms the test adds carry a random suffix and are deleted afterwards.
 */
class ContentTermsIntegrationTest extends AbstractIntegrationTest {

    static final String REFUSAL = "We can't print copyrighted heroes or their names, but your own hero is welcome. Try your own hero's name.";
    static final List<String> SEEDED = List.of("Marvel", "Marvel Studios", "DC", "DC Comics", "Spider-Man", "Iron Man", "Captain America", "Thor",
            "Hulk", "Black Panther", "Wolverine", "Deadpool", "Batman", "Superman", "Wonder Woman", "Joker", "Harley Quinn", "Aquaman", "The Flash",
            "Green Lantern", "Chacha Chaudhary", "Nagraj", "Super Commando Dhruv", "Doga");

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffAccounts staffAccounts;

    @Test
    void theSeededRulesAreListedForEveryStaffRole() {
        String[] owner = staffOwner();
        JsonNode terms = body(get("/admin/api/content-terms", owner));
        assertThat(terms.findValuesAsText("term")).containsExactlyInAnyOrderElementsOf(SEEDED);
        assertThat(terms.findValuesAsText("kind").stream().filter("trademark"::equals)).hasSize(4);
        assertThat(terms.findValuesAsText("kind").stream().filter("character"::equals)).hasSize(20);
        List<String> keys = terms.findValuesAsText("normalised_term");
        assertThat(keys).isSorted();

        JsonNode marvel = byTerm(terms, "Marvel");
        assertThat(marvel.get("id").asText()).hasSize(36);
        assertThat(marvel.get("kind").asText()).isEqualTo("trademark");
        assertThat(marvel.get("reason").asText()).isEqualTo("Marvel Comics brand (Disney)");
        assertThat(marvel.get("active").asBoolean()).isTrue();
        assertThat(marvel.get("normalised_term").asText()).isEqualTo("marvel");
        assertThat(marvel.get("whole_word").asBoolean()).as("six letters: a whole word only").isTrue();
        assertThat(marvel.get("created_at").asText()).endsWith("Z");
        assertThat(marvel.get("updated_at").asText()).endsWith("Z");
        assertThat(byTerm(terms, "DC").get("whole_word").asBoolean()).isTrue();
        assertThat(byTerm(terms, "Iron Man").get("normalised_term").asText()).isEqualTo("ironman");
        assertThat(byTerm(terms, "Iron Man").get("whole_word").asBoolean()).isFalse();
        assertThat(byTerm(terms, "Spider-Man").get("normalised_term").asText()).isEqualTo("spiderman");
        assertThat(byTerm(terms, "Nagraj").get("reason").asText()).isEqualTo("Raj Comics character");

        // the migration's literal keys are the letters and digits of each term, lowercased
        jdbc.queryForList("select term, normalised_term from content_terms").forEach(row -> assertThat(row.get("normalised_term"))
                .as("%s", row.get("term")).isEqualTo(((String) row.get("term")).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")));

        // any staff role reads the list; nobody without a staff token does
        assertThat(body(get("/admin/api/content-terms", studioRole("reader")))).isEqualTo(terms);
        assertProblem(get("/admin/api/content-terms"), HttpStatus.UNAUTHORIZED, "unauthenticated");
    }

    @Test
    void ownersAddAndEditRulesAuditedAndTheStudioRoleOnlyReads() {
        String[] owner = staffOwner();
        String suffix = suffix();
        String term = "Kaptaan Zorbo " + suffix;
        String shortTerm = "Zx" + suffix.substring(0, 2);
        try {
            ResponseEntity<String> created = post("/admin/api/content-terms",
                    write(Map.of("term", "  " + term + " ", "kind", "character", "reason", "Test villain")), owner);
            assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
            JsonNode zorbo = body(created);
            String id = zorbo.get("id").asText();
            assertThat(zorbo.get("term").asText()).isEqualTo(term);
            assertThat(zorbo.get("kind").asText()).isEqualTo("character");
            assertThat(zorbo.get("reason").asText()).isEqualTo("Test villain");
            assertThat(zorbo.get("active").asBoolean()).as("active by default").isTrue();
            assertThat(zorbo.get("normalised_term").asText()).isEqualTo("kaptaanzorbo" + suffix);
            assertThat(zorbo.get("whole_word").asBoolean()).isFalse();
            assertThat(byTerm(body(get("/admin/api/content-terms", owner)), term)).isEqualTo(zorbo);
            JsonNode zx = body(post("/admin/api/content-terms", write(Map.of("term", shortTerm, "kind", "other")), owner));
            assertThat(zx.get("whole_word").asBoolean()).isTrue();
            assertThat(zx.get("reason").isNull()).isTrue();

            // one rule per set of letters and digits, whatever the spelling
            JsonNode duplicate = assertProblem(post("/admin/api/content-terms", write(Map.of("term", "kaptaan-ZORBO " + suffix, "kind", "other")), owner),
                    HttpStatus.CONFLICT, "content_term_exists");
            assertThat(duplicate.get("detail").asText()).contains(term);
            assertThat(assertProblem(post("/admin/api/content-terms", write(Map.of("term", "IRON MAN", "kind", "character")), owner), HttpStatus.CONFLICT,
                    "content_term_exists").get("detail").asText()).contains("Iron Man");
            // what the schema and the rule refuse
            assertProblem(post("/admin/api/content-terms", write(Map.of("term", "!!", "kind", "other")), owner), HttpStatus.UNPROCESSABLE_ENTITY,
                    "validation_failed");
            assertProblem(post("/admin/api/content-terms", write(Map.of("term", "Z.", "kind", "other")), owner), HttpStatus.UNPROCESSABLE_ENTITY,
                    "validation_failed");
            assertProblem(post("/admin/api/content-terms", write(Map.of("term", "Zed " + suffix, "kind", "brand")), owner), HttpStatus.UNPROCESSABLE_ENTITY,
                    "validation_failed");
            assertProblem(post("/admin/api/content-terms", write(Map.of("kind", "other")), owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/content-terms", write(Map.of("term", "z".repeat(81), "kind", "other")), owner), HttpStatus.UNPROCESSABLE_ENTITY,
                    "validation_failed");
            assertProblem(post("/admin/api/content-terms", write(Map.of("term", "Zed " + suffix, "kind", "other", "reason", "r".repeat(201))), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");

            // kind, reason and the switch change; the term is the key and stays as stored
            ResponseEntity<String> updated = put("/admin/api/content-terms/" + id,
                    write(Map.of("term", term, "kind", "other", "reason", "Changed reason", "active", false)), owner);
            assertThat(updated.getStatusCode()).as(updated.getBody()).isEqualTo(HttpStatus.OK);
            JsonNode off = body(updated);
            assertThat(off.get("kind").asText()).isEqualTo("other");
            assertThat(off.get("reason").asText()).isEqualTo("Changed reason");
            assertThat(off.get("active").asBoolean()).isFalse();
            assertThat(off.get("created_at")).isEqualTo(zorbo.get("created_at"));
            JsonNode respelled = body(put("/admin/api/content-terms/" + id, write(Map.of("term", "KAPTAAN-ZORBO " + suffix, "kind", "other", "active", true)), owner));
            assertThat(respelled.get("term").asText()).as("same letters and digits: accepted, the stored spelling stays").isEqualTo(term);
            assertThat(respelled.get("active").asBoolean()).isTrue();
            assertThat(respelled.get("reason").isNull()).as("a PUT replaces the reason").isTrue();
            JsonNode rename = assertProblem(put("/admin/api/content-terms/" + id, write(Map.of("term", "Kaptaan Zorba " + suffix, "kind", "other")), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertThat(rename.get("detail").asText()).contains("can't be renamed");
            assertProblem(put("/admin/api/content-terms/" + UUID.randomUUID(), write(Map.of("term", term, "kind", "other")), owner), HttpStatus.NOT_FOUND,
                    "not_found");

            // the studio role reads and writes nothing here
            String[] karigar = studioRole("karigar");
            assertThat(get("/admin/api/content-terms", karigar).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertProblem(post("/admin/api/content-terms", write(Map.of("term", "Zed " + suffix, "kind", "other")), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(put("/admin/api/content-terms/" + id, write(Map.of("term", term, "kind", "trademark")), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertThat(byTerm(body(get("/admin/api/content-terms", owner)), term).get("kind").asText()).as("nothing changed").isEqualTo("other");

            // audited with the term as the target and the row before and after
            List<JsonNode> entries = body(get("/admin/api/audit?size=200", owner)).get("items").findParents("action").stream()
                    .filter(e -> e.get("target").asText().equals(term)).toList();
            assertThat(entries).extracting(e -> e.get("action").asText()).containsExactly("content_term.update", "content_term.update", "content_term.create");
            assertThat(entries).allSatisfy(e -> assertThat(e.get("staff_email").asText()).isEqualTo("studio@aakar.local"));
            assertThat(entries.get(2).get("before").isNull()).isTrue();
            assertThat(entries.get(2).get("after").get("kind").asText()).isEqualTo("character");
            assertThat(entries.get(1).get("before").get("active").asBoolean()).isTrue();
            assertThat(entries.get(1).get("after").get("active").asBoolean()).isFalse();
            assertThat(entries.get(1).get("after").get("reason").asText()).isEqualTo("Changed reason");

            // and the served OpenAPI document lists the endpoints
            assertThat(body(get("/v3/api-docs")).get("paths").fieldNames()).toIterable()
                    .contains("/admin/api/content-terms", "/admin/api/content-terms/{termId}");
        } finally {
            jdbc.update("delete from content_terms where normalised_term in (?, ?)", "kaptaanzorbo" + suffix, shortTerm.toLowerCase(Locale.ROOT));
        }
    }

    @Test
    void aNameNamingAProtectedHeroIsRefusedWhateverItsSpelling() {
        String[] guest = guest(UUID.randomUUID());
        // case, spacing and punctuation never hide a term (every text fits the keychain's sixteen characters)
        for (String text : List.of("Batman", "bAtMaN", "Bat Man", "B.A.T.M.A.N", "iron man", "Iron-Man", "IRONMAN", "S.P.I.D.E.R-MAN", "spiderman fan",
                "DC", "Thor's Hammer", "Marvel's hero", "chacha-chaudhary", "Nagraj")) {
            refused(post("/api/designs", naamDesign(text), guest), text);
        }
        // the answer never names the term or the list
        ResponseEntity<String> batman = post("/api/designs", naamDesign("Batman"), guest);
        assertThat(batman.getBody()).doesNotContainIgnoringCase("batman").doesNotContainIgnoringCase("marvel").doesNotContainIgnoringCase("warner");

        // short terms only as whole words: names that merely contain them print
        for (String text : List.of("Asha Thorat", "Marvellous Mum", "Adcock", "Nagrajan")) {
            ResponseEntity<String> accepted = post("/api/designs", naamDesign(text), guest);
            assertThat(accepted.getStatusCode()).as("%s: %s", text, accepted.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    @Test
    void aParamsEditIsCheckedTooNewTextsAndKeptOnes() {
        String[] guest = guest(UUID.randomUUID());
        String[] owner = staffOwner();
        String suffix = suffix();
        String term = "Zorbo " + suffix; // twelve characters: fits the keychain's sixteen
        try {
            JsonNode asha = readyDesign(guest, "Asha");
            String version = asha.get("latest_version").get("id").asText();
            refused(post("/api/versions/" + version + "/params", write(Map.of("params", Map.of(),
                    "features", List.of(Map.of("type", "emboss_text", "text", "Wolverine", "anchor", "back")))), guest), "Wolverine");
            assertThat(post("/api/versions/" + version + "/params", "{\"params\": {}}", guest).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

            // a text printed before its term was added is refused on the next edit
            JsonNode zorbo = readyDesign(guest, term);
            String zorboVersion = zorbo.get("latest_version").get("id").asText();
            String id = body(post("/admin/api/content-terms", write(Map.of("term", term, "kind", "other", "reason", "Test hero")), owner)).get("id").asText();
            refused(post("/api/versions/" + zorboVersion + "/params", "{\"params\": {}}", guest), term);
            refused(post("/api/designs", naamDesign(term), guest), term);
            // an upload naming it waits for a reviewer, with the term in the reason
            JsonNode held = uploaded(guest, "zorbo_" + suffix + "_v1.png", "image", SampleFiles.png());
            assertThat(held.get("status").asText()).isEqualTo("pending_review");
            assertThat(reviewReason(owner, held.get("id").asText())).isEqualTo("File name mentions \"" + term + "\", a protected term · Test hero");

            // switched off, it blocks nothing any more
            JsonNode off = body(put("/admin/api/content-terms/" + id, write(Map.of("term", term, "kind", "other", "active", false)), owner));
            assertThat(off.get("active").asBoolean()).isFalse();
            assertThat(post("/api/versions/" + zorboVersion + "/params", "{\"params\": {}}", guest).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(post("/api/designs", naamDesign(term), guest).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(uploaded(guest, "zorbo-" + suffix + "-v2.png", "image", SampleFiles.png()).get("status").asText()).isEqualTo("ready");
        } finally {
            jdbc.update("delete from content_terms where normalised_term = ?", "zorbo" + suffix);
        }
    }

    @Test
    void anUploadNamingAHeroWaitsForReviewWithTheTermInTheReason() {
        String[] guest = guest(UUID.randomUUID());
        String[] owner = staffOwner();
        JsonNode ironMan = uploaded(guest, "IronMan_final.stl", "model", SampleFiles.binaryStl());
        assertThat(ironMan.get("status").asText()).isEqualTo("pending_review");
        assertThat(ironMan.get("url").isNull()).isTrue();
        assertThat(reviewReason(owner, ironMan.get("id").asText())).isEqualTo("File name mentions \"Iron Man\", a protected character · Marvel character (Disney)");
        // a term only the portal lists (not aakar.uploads.flag-terms), and a short one standing as a word
        assertThat(reviewReason(owner, uploaded(guest, "Wolverine-claws.obj", "model", SampleFiles.obj()).get("id").asText()))
                .isEqualTo("File name mentions \"Wolverine\", a protected character · Marvel character (Disney)");
        assertThat(reviewReason(owner, uploaded(guest, "DC_logo.png", "image", SampleFiles.png()).get("id").asText()))
                .isEqualTo("File name mentions \"DC\", a protected trademark · DC Comics brand (Warner Bros. Discovery)");
        // a name that merely contains a short term is clean
        assertThat(uploaded(guest, "thorat-family.png", "image", SampleFiles.png()).get("status").asText()).isEqualTo("ready");
    }

    // ---- helpers --------------------------------------------------------------------------------------------------------

    /** 422 {@code protected_term} with the customer's copy, pointing at the text, and nothing else: never the term or the list. */
    private void refused(ResponseEntity<String> response, String text) {
        JsonNode problem = assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY, "protected_term");
        assertThat(problem.get("detail").asText()).as(text).isEqualTo(REFUSAL);
        assertThat(problem.get("title").asText()).isEqualTo("Protected term");
        assertThat(problem.get("field").asText()).isEqualTo("features[0].text");
        assertThat(problem.get("feature").asInt()).isZero();
        List<String> keys = new ArrayList<>();
        problem.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).as("%s: only the problem and where it is", text).isSubsetOf("type", "title", "status", "detail", "instance", "code", "feature", "field");
    }

    /** A keychain with the text (Naam) on its back, built and ready. */
    private JsonNode readyDesign(String[] identity, String text) {
        ResponseEntity<String> accepted = post("/api/designs", naamDesign(text), identity);
        assertThat(accepted.getStatusCode()).as(accepted.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        awaitJob(body(accepted).get("job_id").asText(), "succeeded");
        return body(get("/api/designs/" + body(accepted).get("design_id").asText()));
    }

    private String naamDesign(String text) {
        return write(Map.of("source", "create", "family_id", "keychain",
                "features", List.of(Map.of("type", "emboss_text", "text", text, "anchor", "back"))));
    }

    private String reviewReason(String[] owner, String uploadId) {
        List<JsonNode> rows = body(get("/admin/api/uploads?status=pending_review&limit=200", owner)).findParents("id").stream()
                .filter(u -> u.get("id").asText().equals(uploadId) && u.has("kind")).toList();
        assertThat(rows).as("upload %s in the review queue", uploadId).hasSize(1);
        return rows.get(0).get("review").get("reason").asText();
    }

    private String[] studioRole(String name) {
        String email = name + "-content-" + suffix() + "@aakar.local";
        staffAccounts.create(email, "Content Desk", StaffRole.studio, "aakar-" + name + "-pass");
        return bearer(body(post("/admin/api/auth/login", write(Map.of("email", email, "password", "aakar-" + name + "-pass")))).get("access_token").asText());
    }

    private static JsonNode byTerm(JsonNode terms, String term) {
        List<JsonNode> rows = terms.findParents("term").stream().filter(t -> t.get("term").asText().equals(term)).toList();
        assertThat(rows).as("term %s", term).hasSize(1);
        return rows.get(0);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }
}
