package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Customer uploads (plan §4, ADR-0014): format sniffing by extension and magic bytes, size limits, owner-only reads, the
 * sign-in hand-over, and the content review queue: a flagged file waits (409 on a design), a reviewer approves it (the
 * design starts) or turns it down with a note the customer reads (422 on a design). Audited as {@code review.decide}.
 */
class UploadsIntegrationTest extends AbstractIntegrationTest {

    static final String KEYCHAIN_WITH = """
            {"source": "create", "family_id": "keychain", "material": "indigo_matte",
             "features": [{"type": "relief_image", "source": {"upload_id": "%s"}, "anchor": "face"}]}
            """;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    StaffAccounts staffAccounts;

    @Test
    void photosAndModelFilesAreSniffedStoredAndServedToTheirOwnerOnly() throws Exception {
        UUID guestId = UUID.randomUUID();
        String[] asha = guest(guestId);
        byte[] png = SampleFiles.png();

        JsonNode photo = uploaded(asha, "asha-birthday.png", "image", png);
        String id = photo.get("id").asText();
        assertThat(photo.get("kind").asText()).isEqualTo("image");
        assertThat(photo.get("format").asText()).isEqualTo("png");
        assertThat(photo.get("bytes").asLong()).isEqualTo(png.length);
        assertThat(photo.get("sha256").asText()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)));
        assertThat(photo.get("status").asText()).isEqualTo("ready");
        assertThat(photo.get("message").isNull()).isTrue();
        assertThat(photo.get("created_at").asText()).endsWith("Z");
        assertThat(photo.has("internal_url")).isFalse();
        assertThat(photo.has("origin")).isFalse();
        // uploads/<owner digest>/<id>.png on the public origin; the guest id (a guest's credential) never appears in a URL
        String url = photo.get("url").asText();
        assertThat(url).startsWith("http://localhost:8080/media/uploads/").endsWith("/" + id + ".png").doesNotContain(guestId.toString());
        ResponseEntity<byte[]> served = getBytes(URI.create(url).getPath());
        assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(served.getBody()).isEqualTo(png);
        assertThat(jdbc.queryForObject("select storage_key from uploads where id = ?::uuid", String.class, id)).isEqualTo(URI.create(url).getPath()
                .substring("/media/".length()));

        // the owner reads it back; another guest and an anonymous caller get 404, never a hint that it exists
        assertThat(body(get("/api/uploads/" + id, asha))).isEqualTo(photo);
        assertProblem(get("/api/uploads/" + id, guest(UUID.randomUUID())), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(get("/api/uploads/" + id), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(get("/api/uploads/" + UUID.randomUUID(), asha), HttpStatus.NOT_FOUND, "not_found");

        // every image and model format the contract names, recognised by extension and first bytes
        assertThat(uploaded(asha, "portrait.JPEG", "image", SampleFiles.jpeg()).get("format").asText()).isEqualTo("jpg");
        assertThat(uploaded(asha, "portrait.webp", "image", SampleFiles.webp()).get("format").asText()).isEqualTo("webp");
        assertThat(uploaded(asha, "portrait.heic", "image", SampleFiles.heic()).get("format").asText()).isEqualTo("heic");
        Map<String, byte[]> models = new LinkedHashMap<>();
        models.put("vase.stl", SampleFiles.binaryStl());
        models.put("tile.stl", SampleFiles.asciiStl());
        models.put("vase.glb", SampleFiles.glb());
        models.put("vase.3mf", SampleFiles.threeMf());
        models.put("vase.obj", SampleFiles.obj());
        models.put("vase.ply", SampleFiles.ply());
        models.put("vase.off", SampleFiles.off());
        models.put("vase.gltf", SampleFiles.gltf());
        for (Map.Entry<String, byte[]> model : models.entrySet()) {
            JsonNode stored = uploaded(asha, model.getKey(), "model", model.getValue());
            assertThat(stored.get("kind").asText()).isEqualTo("model");
            assertThat(stored.get("format").asText()).as(model.getKey()).isEqualTo(model.getKey().substring(model.getKey().indexOf('.') + 1));
            assertThat(stored.get("status").asText()).isEqualTo("ready");
        }
    }

    @Test
    void refusedUploadsCarryStableCodes() {
        String[] guest = guest(UUID.randomUUID());
        long storedBefore = jdbc.queryForObject("select count(*) from uploads", Long.class);

        // too large for its kind: photos up to 15 MB (models may be 50 MB)
        JsonNode tooLarge = assertProblem(upload(guest, "huge.png", "image", SampleFiles.pngOfSize(15 * 1024 * 1024 + 1)),
                HttpStatus.PAYLOAD_TOO_LARGE, "payload_too_large");
        assertThat(tooLarge.get("detail").asText()).isEqualTo("Photos can be up to 15 MB; this one is 15.1 MB.");
        assertThat(tooLarge.get("max_bytes").asLong()).isEqualTo(15L * 1024 * 1024);

        // not a format of that kind, or not what its name claims
        JsonNode txt = assertProblem(upload(guest, "notes.txt", "image", "hello".getBytes()), HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_format");
        assertThat(txt.get("detail").asText()).isEqualTo("That isn't a photo we can use. Upload a PNG, JPG, WEBP or HEIC image.");
        assertProblem(upload(guest, "notes.txt", "model", "hello".getBytes()), HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_format");
        assertProblem(upload(guest, "vase.stl", "image", SampleFiles.binaryStl()), HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_format");
        assertProblem(upload(guest, "photo.png", "model", SampleFiles.png()), HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_format");
        JsonNode renamed = assertProblem(upload(guest, "vase.stl", "model", SampleFiles.png()), HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_format");
        assertThat(renamed.get("detail").asText()).contains("doesn't look like a real STL model file");
        assertThat(renamed.get("format").asText()).isEqualTo("stl");
        assertProblem(upload(guest, "photo.png", "image", "not really a png".getBytes()), HttpStatus.UNPROCESSABLE_ENTITY, "unsupported_format");

        // malformed requests
        assertProblem(upload(guest, "photo.png", "video", SampleFiles.png()), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(upload(guest, "photo.png", null, SampleFiles.png()), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(upload(guest, "photo.png", "image", new byte[0]), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/uploads", "{\"kind\": \"image\"}", guest), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "validation_failed");
        // uploads need a known identity, like the cart
        assertProblem(upload(new String[0], "photo.png", "image", SampleFiles.png()), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertThat(jdbc.queryForObject("select count(*) from uploads", Long.class)).as("nothing refused was stored").isEqualTo(storedBefore);
    }

    @Test
    void aGuestsUploadsFollowThemIntoTheirAccount() {
        UUID guestId = UUID.randomUUID();
        JsonNode photo = uploaded(guest(guestId), "asha.png", "image", SampleFiles.png());
        String token = signInAs(phone(), guestId);

        assertThat(body(get("/api/uploads/" + photo.get("id").asText(), bearer(token))).get("status").asText()).isEqualTo("ready");
        assertProblem(get("/api/uploads/" + photo.get("id").asText(), guest(guestId)), HttpStatus.NOT_FOUND, "not_found");
        // the signed-in customer can place it on a design
        assertThat(post("/api/designs", KEYCHAIN_WITH.formatted(photo.get("id").asText()), bearer(token)).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        // and uploads as themselves from now on
        assertThat(uploaded(bearer(token), "second.png", "image", SampleFiles.png()).get("status").asText()).isEqualTo("ready");
    }

    @Test
    void aFlaggedFileWaitsForAReviewerAndAnApprovalReleasesIt() {
        UUID guestId = UUID.randomUUID();
        String[] guest = guest(guestId);
        JsonNode flagged = uploaded(guest, "Marvel_IronMan-poster.png", "image", SampleFiles.png());
        String uploadId = flagged.get("id").asText();
        assertThat(flagged.get("status").asText()).isEqualTo("pending_review");
        assertThat(flagged.get("url").isNull()).as("no browser URL until it is cleared").isTrue();

        // it cannot go on a design yet
        JsonNode waiting = assertProblem(post("/api/designs", KEYCHAIN_WITH.formatted(uploadId), guest), HttpStatus.CONFLICT, "upload_not_ready");
        assertThat(waiting.get("detail").asText()).isEqualTo("The studio is still checking the file for the photo relief (Chhavi); you can use it once it is cleared.");
        assertThat(waiting.get("upload_id").asText()).isEqualTo(uploadId);

        // the review queue shows it with its owner, origin, reason and a URL staff can open
        String[] owner = staffOwner();
        JsonNode row = find(body(get("/admin/api/uploads?status=pending_review", owner)), uploadId);
        assertThat(row.get("status").asText()).isEqualTo("pending_review");
        assertThat(row.get("url").asText()).endsWith(uploadId + ".png");
        assertThat(row.get("origin").asText()).isEqualTo("upload");
        assertThat(row.get("owner").get("guest_id").asText()).isEqualTo(guestId.toString());
        assertThat(row.get("owner").get("user_id").isNull()).isTrue();
        assertThat(row.get("owner").get("phone").isNull()).isTrue();
        assertThat(row.get("review").get("status").asText()).isEqualTo("pending");
        assertThat(row.get("review").get("reason").asText()).isEqualTo("File name mentions \"marvel\", a flagged term");
        assertThat(row.get("review").get("decided_at").isNull()).isTrue();
        String reviewId = row.get("review").get("id").asText();
        assertThat(body(get("/admin/api/uploads?status=ready&limit=200", owner)).findValuesAsText("id")).doesNotContain(uploadId);
        assertThat(body(get("/admin/api/uploads", owner)).findValuesAsText("id")).contains(uploadId);

        // a studio-role reviewer may decide (reviews are not owner-only)
        String reviewer = "reviewer-" + UUID.randomUUID().toString().substring(0, 6) + "@aakar.local";
        staffAccounts.create(reviewer, "Review Desk", StaffRole.studio, "aakar-reviewer");
        String[] studio = bearer(body(post("/admin/api/auth/login", "{\"email\": \"" + reviewer + "\", \"password\": \"aakar-reviewer\"}"))
                .get("access_token").asText());
        ResponseEntity<String> approved = post("/admin/api/content-reviews/" + reviewId, "{\"decision\": \"approved\", \"note\": \"Original art\"}", studio);
        assertThat(approved.getStatusCode()).as(approved.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode decided = body(approved);
        assertThat(decided.get("id").asText()).isEqualTo(uploadId);
        assertThat(decided.get("status").asText()).isEqualTo("ready");
        assertThat(decided.get("review").get("status").asText()).isEqualTo("approved");
        assertThat(decided.get("review").get("reviewer_email").asText()).isEqualTo(reviewer);
        assertThat(decided.get("review").get("decision_note").asText()).isEqualTo("Original art");
        assertThat(decided.get("review").get("decided_at").asText()).endsWith("Z");
        assertProblem(post("/admin/api/content-reviews/" + reviewId, "{\"decision\": \"rejected\"}", owner), HttpStatus.CONFLICT, "review_already_decided");

        // the customer's copy is ready with its browser URL, and the design starts
        JsonNode cleared = body(get("/api/uploads/" + uploadId, guest));
        assertThat(cleared.get("status").asText()).isEqualTo("ready");
        assertThat(cleared.get("url").asText()).endsWith(uploadId + ".png");
        assertThat(cleared.get("message").isNull()).isTrue();
        assertThat(post("/api/designs", KEYCHAIN_WITH.formatted(uploadId), guest).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // audited as review.decide with before/after
        JsonNode entry = body(get("/admin/api/audit?size=200", owner)).get("items").findParents("target").stream()
                .filter(e -> e.get("target").asText().equals(reviewId)).findFirst().orElseThrow();
        assertThat(entry.get("action").asText()).isEqualTo("review.decide");
        assertThat(entry.get("staff_email").asText()).isEqualTo(reviewer);
        assertThat(entry.get("before").get("status").asText()).isEqualTo("pending");
        assertThat(entry.get("before").get("upload_status").asText()).isEqualTo("pending_review");
        assertThat(entry.get("after").get("status").asText()).isEqualTo("approved");
        assertThat(entry.get("after").get("upload_status").asText()).isEqualTo("ready");
    }

    @Test
    void aRejectedFileExplainsWhyAndCannotBeUsed() {
        String phone = phone();
        String token = signIn(phone);
        String[] customer = bearer(token);
        JsonNode flagged = uploaded(customer, "iron man hero.jpg", "image", SampleFiles.jpeg());
        String uploadId = flagged.get("id").asText();
        assertThat(flagged.get("status").asText()).isEqualTo("pending_review");

        String[] owner = staffOwner();
        JsonNode row = find(body(get("/admin/api/uploads?status=pending_review", owner)), uploadId);
        assertThat(row.get("owner").get("phone").asText()).isEqualTo(phone);
        assertThat(row.get("owner").get("user_id").asText()).isEqualTo(body(get("/api/auth/me", customer)).get("id").asText());
        String reviewId = row.get("review").get("id").asText();
        String note = "We can't print copyrighted heroes, but your own hero is welcome.";
        JsonNode rejected = body(post("/admin/api/content-reviews/" + reviewId, "{\"decision\": \"rejected\", \"note\": \"" + note + "\"}", owner));
        assertThat(rejected.get("status").asText()).isEqualTo("rejected");
        assertThat(rejected.get("message").asText()).isEqualTo(note);
        assertThat(rejected.get("url").asText()).as("staff can still open it").endsWith(".jpg");

        // the customer reads the reviewer's note, gets no URL, and cannot place the file
        JsonNode mine = body(get("/api/uploads/" + uploadId, customer));
        assertThat(mine.get("status").asText()).isEqualTo("rejected");
        assertThat(mine.get("message").asText()).isEqualTo(note);
        assertThat(mine.get("url").isNull()).isTrue();
        JsonNode refused = assertProblem(post("/api/designs", KEYCHAIN_WITH.formatted(uploadId), customer), HttpStatus.UNPROCESSABLE_ENTITY, "upload_rejected");
        assertThat(refused.get("detail").asText()).isEqualTo("The file for the photo relief (Chhavi) can't be printed: " + note);
        assertThat(find(body(get("/admin/api/uploads?status=rejected", owner)), uploadId).get("review").get("decision_note").asText()).isEqualTo(note);

        // queue and decision validation
        assertProblem(post("/admin/api/content-reviews/" + reviewId, "{\"decision\": \"approved\"}", owner), HttpStatus.CONFLICT, "review_already_decided");
        assertProblem(post("/admin/api/content-reviews/" + UUID.randomUUID(), "{\"decision\": \"approved\"}", owner), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(post("/admin/api/content-reviews/" + reviewId, "{\"decision\": \"maybe\"}", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertProblem(post("/admin/api/content-reviews/" + reviewId, "{}", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertProblem(get("/admin/api/uploads?status=flagged", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertProblem(get("/admin/api/uploads?limit=0", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertProblem(get("/admin/api/uploads?limit=201", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertThat(body(get("/admin/api/uploads?limit=1", owner))).hasSize(1);
        assertProblem(get("/admin/api/uploads"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/admin/api/uploads", customer), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(post("/admin/api/content-reviews/" + reviewId, "{\"decision\": \"approved\"}", customer), HttpStatus.UNAUTHORIZED, "unauthenticated");
    }

    /** Signs in through the mock OTP while sending the guest header, so the guest's designs, uploads and cart move over. */
    private String signInAs(String phone, UUID guestId) {
        JsonNode otp = body(post("/api/auth/otp/request", "{\"phone\": \"" + phone + "\"}"));
        ResponseEntity<String> verified = post("/api/auth/otp/verify",
                "{\"request_id\": \"" + otp.get("request_id").asText() + "\", \"code\": \"" + otp.get("dev_code").asText() + "\"}", guest(guestId));
        assertThat(verified.getStatusCode()).as(verified.getBody()).isEqualTo(HttpStatus.OK);
        return body(verified).get("access_token").asText();
    }

    private static JsonNode find(JsonNode uploads, String id) {
        List<JsonNode> rows = uploads.findParents("id").stream().filter(u -> u.get("id").asText().equals(id) && u.has("kind")).toList();
        assertThat(rows).as("upload %s in %s", id, uploads).hasSize(1);
        return rows.get(0);
    }
}
