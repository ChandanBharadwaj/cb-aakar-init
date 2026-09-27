package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import studio.aakar.api.admin.StaffAccounts;
import studio.aakar.api.admin.StaffRole;
import studio.aakar.api.pricing.PricingPolicy;
import studio.aakar.api.pricing.PricingPolicyStore;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.GeometryStub;

/**
 * The management API (ADR-0012) end to end: staff sign-in with tokens distinct from customer tokens, the studio
 * queue and dashboard, an order advanced from queued to delivered with events and messages, the print pack, QC
 * photo, packaging card and share code, then configuration (pricing, materials, catalog, templates) with its audit
 * trail and the read-only {@code studio} role.
 */
class AdminLoopIntegrationTest extends AbstractIntegrationTest {

    static final String OWNER_LOGIN = "{\"email\": \"studio@aakar.local\", \"password\": \"aakar-studio\"}";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PricingPolicyStore policies;
    @Autowired
    StaffAccounts staffAccounts;

    @Test
    void staffTokensAndCustomerTokensNeverCross() {
        ResponseEntity<String> login = post("/admin/api/auth/login", OWNER_LOGIN);
        assertThat(login.getStatusCode()).as(login.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode session = body(login);
        String staff = session.get("access_token").asText();
        assertThat(staff.split("\\.")).hasSize(3);
        assertThat(session.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(session.get("expires_in_s").asLong()).isEqualTo(12 * 3600);
        assertThat(session.get("staff").get("email").asText()).isEqualTo("studio@aakar.local");
        assertThat(session.get("staff").get("role").asText()).isEqualTo("owner");
        assertThat(session.get("staff").get("name").asText()).isEqualTo("Aakar Studio");
        JsonNode me = body(get("/admin/api/auth/me", bearer(staff)));
        assertThat(me.get("id").asText()).isEqualTo(session.get("staff").get("id").asText());
        assertThat(me.get("role").asText()).isEqualTo("owner");

        assertProblem(post("/admin/api/auth/login", "{\"email\": \"studio@aakar.local\", \"password\": \"wrong-password\"}"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(post("/admin/api/auth/login", "{\"email\": \"nobody@aakar.local\", \"password\": \"aakar-studio\"}"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(post("/admin/api/auth/login", "{\"email\": \"studio@aakar.local\", \"password\": \"short\"}"), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");

        // no token, a garbage token and a customer token are all refused by the staff chain
        assertProblem(get("/admin/api/dashboard"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/admin/api/orders", bearer("garbage")), HttpStatus.UNAUTHORIZED, "unauthenticated");
        String customer = signIn(phone());
        assertProblem(get("/admin/api/dashboard", bearer(customer)), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/admin/api/auth/me", bearer(customer)), HttpStatus.UNAUTHORIZED, "unauthenticated");
        // and a staff token is never a customer
        assertProblem(get("/api/orders", bearer(staff)), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/api/auth/me", bearer(staff)), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/api/cart", bearer(staff)), HttpStatus.UNAUTHORIZED, "unauthenticated");
        // customer routes still work for the customer
        assertThat(get("/api/auth/me", bearer(customer)).getStatusCode()).isEqualTo(HttpStatus.OK);
        // OpenAPI lists the admin paths under the staff scheme
        JsonNode docs = body(get("/v3/api-docs"));
        assertThat(docs.get("paths").fieldNames()).toIterable().contains("/admin/api/auth/login", "/admin/api/dashboard", "/admin/api/orders",
                "/admin/api/orders/{orderId}/advance", "/admin/api/orders/{orderId}/print-pack", "/admin/api/orders/{orderId}/packaging-card.pdf",
                "/admin/api/pricing/policies", "/admin/api/materials/{materialId}", "/admin/api/templates/{templateId}", "/admin/api/audit");
        assertThat(docs.get("components").get("securitySchemes").get("staffBearer").get("scheme").asText()).isEqualTo("bearer");
    }

    @Test
    void staffRunAnOrderFromTheQueueToDelivered() throws Exception {
        String[] staff = bearer(body(post("/admin/api/auth/login", OWNER_LOGIN)).get("access_token").asText());
        PaidOrder paid = paidOrder();
        String orderId = paid.orderId();

        // dashboard counts the new order
        JsonNode dashboard = body(get("/admin/api/dashboard", staff));
        assertThat(dashboard.get("orders_by_status").get("queued").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.get("orders_by_status").has("cancelled")).isTrue();
        assertThat(dashboard.get("orders_today").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.get("revenue_today_paise").asLong()).isGreaterThanOrEqualTo(114_900);
        assertThat(dashboard.get("revenue_month_paise").asLong()).isGreaterThanOrEqualTo(dashboard.get("revenue_today_paise").asLong());
        assertThat(dashboard.get("awaiting_action").asLong()).isGreaterThanOrEqualTo(1);

        // the queue: by status and number prefix, and by phone digits
        JsonNode queue = body(get("/admin/api/orders?status=queued,on_hold&q=" + paid.orderNumber(), staff));
        assertThat(queue.get("total").asInt()).isEqualTo(1);
        assertThat(queue.get("page").asInt()).isZero();
        assertThat(queue.get("size").asInt()).isEqualTo(25);
        JsonNode row = queue.get("items").get(0);
        assertThat(row.get("id").asText()).isEqualTo(orderId);
        assertThat(row.get("number").asText()).isEqualTo(paid.orderNumber());
        assertThat(row.get("status").asText()).isEqualTo("queued");
        assertThat(row.get("stage").asText()).isEqualTo("queued");
        assertThat(row.get("title").asText()).isEqualTo("Jharokha Phone Stand");
        assertThat(row.get("total_paise").asLong()).isEqualTo(114_900);
        assertThat(row.get("customer").get("id").asText()).isEqualTo(paid.userId());
        assertThat(row.get("customer").get("phone").asText()).isEqualTo(paid.phone());
        assertThat(row.get("materials")).extracting(JsonNode::asText).containsExactly("terracotta_silk");
        assertThat(row.get("next_actions")).extracting(JsonNode::asText).containsExactly("slicing", "on_hold", "cancelled");
        JsonNode byPhone = body(get("/admin/api/orders?q=" + paid.phone().substring(3, 12), staff));
        assertThat(byPhone.get("items")).extracting(i -> i.get("id").asText()).contains(orderId);
        JsonNode byBareNumber = body(get("/admin/api/orders?q=" + paid.orderNumber().substring(3), staff));
        assertThat(byBareNumber.get("items")).extracting(i -> i.get("id").asText()).contains(orderId);
        assertThat(body(get("/admin/api/orders?status=delivered&q=" + paid.orderNumber(), staff)).get("total").asInt()).isZero();
        assertProblem(get("/admin/api/orders?status=flying", staff), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertThat(body(get("/admin/api/orders?size=500", staff)).get("size").asInt()).isEqualTo(100);

        // detail: the customer order shape plus customer, next actions, photos and messages
        JsonNode detail = body(get("/admin/api/orders/" + orderId, staff));
        assertThat(detail.get("status").asText()).isEqualTo("queued");
        assertThat(detail.get("customer").get("id").asText()).isEqualTo(paid.userId());
        assertThat(detail.get("customer").get("phone").asText()).isEqualTo(paid.phone());
        assertThat(detail.get("customer").has("email")).isTrue();
        assertThat(detail.get("next_actions")).extracting(JsonNode::asText).containsExactly("slicing", "on_hold", "cancelled");
        assertThat(detail.get("qc_photos")).isEmpty();
        assertThat(detail.get("notifications")).extracting(n -> n.get("template").asText()).containsExactly("order_confirmed");
        assertThat(detail.get("notifications").get(0).get("order_id").asText()).isEqualTo(orderId);
        assertThat(detail.get("notifications").get(0).get("rendered_text").asText()).contains(paid.orderNumber());
        assertThat(detail.get("events")).hasSize(3);
        assertThat(detail.get("items").get(0).get("assets").get("3mf").get("url").asText()).startsWith(GEOMETRY.baseUrl());
        assertThat(detail.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(detail.get("address").get("pincode").asText()).isEqualTo("560001");
        assertProblem(get("/admin/api/orders/" + UUID.randomUUID(), staff), HttpStatus.NOT_FOUND, "not_found");

        // the packaging card waits for packed; the print pack is ready now
        assertProblem(get("/admin/api/orders/" + orderId + "/packaging-card.pdf", staff), HttpStatus.CONFLICT, "order_not_packed");
        ResponseEntity<byte[]> pack = getBytes("/admin/api/orders/" + orderId + "/print-pack", staff);
        assertThat(pack.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pack.getHeaders().getContentType()).hasToString("application/zip");
        assertThat(pack.getHeaders().getFirst("Content-Disposition")).contains("attachment").contains(paid.orderNumber() + "-print-pack.zip");
        Map<String, byte[]> files = unzip(pack.getBody());
        assertThat(files).containsOnlyKeys("item-1/model.3mf", "item-1/model.stl", "item-1/print-sheet.txt");
        assertThat(new String(files.get("item-1/model.3mf"), StandardCharsets.UTF_8)).isEqualTo(GeometryStub.STUB_3MF);
        assertThat(new String(files.get("item-1/model.stl"), StandardCharsets.UTF_8)).isEqualTo(GeometryStub.STUB_STL);
        String sheet = new String(files.get("item-1/print-sheet.txt"), StandardCharsets.UTF_8);
        assertThat(sheet).contains("AAKAR PRINT SHEET · " + paid.orderNumber() + " · item 1 of 1").contains("Jharokha Phone Stand (design version 1)")
                .contains("jharokha_phone_stand@1").contains("width_mm=92").contains("arch_cusps=5")
                .contains("Terracotta Silk · Silk PLA, copper-terracotta").contains("Finish class:   silk").contains("Quantity:       1")
                .contains("Bounds (mm):    92 × 78 × 120").contains("Mass (each):    64 g").contains("Estimated time: 3 h 40 m")
                .contains("Notes:          Gift wrap please").contains("model.3mf (").contains("model.stl (");

        // queued → slicing with detail, then on through the studio with default and custom messages
        JsonNode slicing = body(post("/admin/api/orders/" + orderId + "/advance",
                "{\"status\": \"slicing\", \"detail\": {\"printer_bay\": \"B2\", \"layer_height_mm\": 0.2, \"layers_total\": 480}}", staff));
        assertThat(slicing.get("status").asText()).isEqualTo("slicing");
        assertThat(slicing.get("next_actions")).extracting(JsonNode::asText).containsExactly("printing", "on_hold", "cancelled");
        JsonNode slicingEvent = slicing.get("events").get(3);
        assertThat(slicingEvent.get("sequence").asInt()).isEqualTo(4);
        assertThat(slicingEvent.get("message").asText()).isEqualTo("Slicing your piece");
        assertThat(slicingEvent.get("detail").get("printer_bay").asText()).isEqualTo("B2");
        assertThat(slicingEvent.get("detail").get("layers_total").asInt()).isEqualTo(480);

        JsonNode printing = body(post("/admin/api/orders/" + orderId + "/advance",
                "{\"status\": \"printing\", \"message\": \"Layer 1 of 480\", \"detail\": {\"layer\": 1, \"layers_total\": 480}}", staff));
        assertThat(printing.get("events").get(4).get("message").asText()).isEqualTo("Layer 1 of 480");
        assertThat(printing.get("notifications")).extracting(n -> n.get("template").asText()).contains("printing_timelapse");
        assertThat(body(post("/admin/api/orders/" + orderId + "/advance", "{\"status\": \"finishing\"}", staff)).get("events").get(5).get("message").asText())
                .isEqualTo("Hand sanding & sealing");
        assertThat(body(post("/admin/api/orders/" + orderId + "/advance", "{\"status\": \"qc\"}", staff)).get("events").get(6).get("message").asText())
                .isEqualTo("Quality check");

        // a QC photo goes into the media store and onto the order
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        ResponseEntity<String> uploaded = upload(orderId, "qc-front.png", MediaType.IMAGE_PNG, png, "Arch edges checked", staff);
        assertThat(uploaded.getStatusCode()).as(uploaded.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode asset = body(uploaded);
        assertThat(asset.get("kind").asText()).isEqualTo("qc_photo");
        assertThat(asset.get("content_type").asText()).isEqualTo("image/png");
        assertThat(asset.get("bytes").asInt()).isEqualTo(png.length);
        assertThat(asset.get("note").asText()).isEqualTo("Arch edges checked");
        assertThat(asset.get("url").asText()).contains("/media/qc/" + paid.orderNumber() + "/").endsWith(".png");
        ResponseEntity<byte[]> served = getBytes(URI.create(asset.get("url").asText()).getPath());
        assertThat(served.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(served.getHeaders().getContentType()).hasToString("image/png");
        assertThat(served.getBody()).isEqualTo(png);
        assertThat(body(get("/admin/api/orders/" + orderId, staff)).get("qc_photos")).hasSize(1);
        assertProblem(upload(orderId, "huge.jpg", MediaType.IMAGE_JPEG, new byte[11 * 1024 * 1024], null, staff), HttpStatus.PAYLOAD_TOO_LARGE, "payload_too_large");

        // packed books the shipment and unlocks the packaging card, whose share code is minted once
        JsonNode packed = body(post("/admin/api/orders/" + orderId + "/advance", "{\"status\": \"packed\"}", staff));
        assertThat(packed.get("events").get(7).get("message").asText()).isEqualTo("Packed");
        assertThat(packed.get("shipment").get("carrier").asText()).isEqualTo("mock-delhivery");
        assertThat(packed.get("shipment").get("awb").asText()).matches("MOCK\\d{10}");
        ResponseEntity<byte[]> card = getBytes("/admin/api/orders/" + orderId + "/packaging-card.pdf", staff);
        assertThat(card.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(card.getHeaders().getContentType()).hasToString("application/pdf");
        assertThat(card.getHeaders().getFirst("Content-Disposition")).contains(paid.orderNumber() + "-packaging-card.pdf");
        assertThat(new String(card.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        List<Map<String, Object>> codes = jdbc.queryForList("select code, design_id::text as design_id, version_id::text as version_id from share_codes where order_id = ?::uuid", orderId);
        assertThat(codes).hasSize(1);
        assertThat((String) codes.get(0).get("code")).matches("^[A-Z2-7]{8}$");
        assertThat(codes.get(0)).containsEntry("design_id", paid.designId()).containsEntry("version_id", paid.versionId());
        assertThat(getBytes("/admin/api/orders/" + orderId + "/packaging-card.pdf", staff).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select count(*) from share_codes where order_id = ?::uuid", Long.class, orderId)).isEqualTo(1L);
        // the code on the card resolves publicly to the piece, never to the customer
        String shareCode = (String) codes.get(0).get("code");
        JsonNode shared = body(get("/api/share/" + shareCode.toLowerCase()));
        assertThat(shared.get("code").asText()).isEqualTo(shareCode);
        assertThat(shared.get("design_id").asText()).isEqualTo(paid.designId());
        assertThat(shared.get("version_id").asText()).isEqualTo(paid.versionId());
        assertThat(shared.get("material_id").asText()).isNotBlank();
        assertThat(shared.get("studio").asText()).isEqualTo("Bengaluru");
        assertThat(shared.get("reprint_path").asText()).isEqualTo("/k/" + shareCode);
        assertThat(shared.get("remix_path").asText()).isEqualTo("/design/" + paid.designId());
        assertThat(shared.has("customer")).isFalse();
        assertThat(shared.toString()).doesNotContain(paid.phone());
        assertProblem(get("/api/share/ZZZZZZZ2"), HttpStatus.NOT_FOUND, "not_found");

        // shipped and delivered: tracking events plus the customer messages
        JsonNode shipped = body(post("/admin/api/orders/" + orderId + "/advance", "{\"status\": \"shipped\"}", staff));
        assertThat(shipped.get("events").get(8).get("message").asText()).isEqualTo("Shipped");
        assertThat(shipped.get("shipment").get("status").asText()).isEqualTo("in_transit");
        JsonNode delivered = body(post("/admin/api/orders/" + orderId + "/advance", "{\"status\": \"delivered\"}", staff));
        assertThat(delivered.get("status").asText()).isEqualTo("delivered");
        assertThat(delivered.get("events")).hasSize(10);
        assertThat(delivered.get("events").get(9).get("message").asText()).isEqualTo("Delivered");
        assertThat(delivered.get("shipment").get("status").asText()).isEqualTo("delivered");
        assertThat(delivered.get("next_actions")).isEmpty();
        assertThat(delivered.get("notifications")).extracting(n -> n.get("template").asText())
                .contains("order_confirmed", "printing_timelapse", "shipped", "delivered");
        JsonNode shippedMessage = body(get("/admin/api/notifications?order_id=" + orderId, staff)).get("items").findParents("template").stream()
                .filter(n -> n.get("template").asText().equals("shipped")).findFirst().orElseThrow();
        assertThat(shippedMessage.get("channel").asText()).isEqualTo("whatsapp");
        assertThat(shippedMessage.get("to").asText()).isEqualTo(paid.phone());
        assertThat(shippedMessage.get("status").asText()).isEqualTo("logged");
        assertThat(shippedMessage.get("rendered_text").asText()).contains("mock-delhivery").contains("MOCK");
        assertThat(shippedMessage.get("payload").get("order_number").asText()).isEqualTo(paid.orderNumber());
        // the customer sees the same events on their side
        assertThat(body(get("/api/orders/" + orderId, bearer(paid.token()))).get("events")).hasSize(10);

        // nothing follows delivered; unknown statuses fail validation
        assertProblem(post("/admin/api/orders/" + orderId + "/advance", "{\"status\": \"cancelled\"}", staff), HttpStatus.CONFLICT, "invalid_transition");
        assertProblem(post("/admin/api/orders/" + orderId + "/advance", "{\"status\": \"flying\"}", staff), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertProblem(post("/admin/api/orders/" + orderId + "/advance", "{}", staff), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
        assertProblem(post("/admin/api/orders/" + UUID.randomUUID() + "/advance", "{\"status\": \"slicing\"}", staff), HttpStatus.NOT_FOUND, "not_found");

        // the audit trail names every move and the photo
        JsonNode audit = body(get("/admin/api/audit?size=200", staff));
        List<JsonNode> mine = audit.get("items").findParents("target").stream().filter(e -> e.get("target").asText().equals(paid.orderNumber())).toList();
        assertThat(mine).extracting(e -> e.get("action").asText()).contains("order.advance", "order.qc_photo");
        assertThat(mine).allSatisfy(e -> assertThat(e.get("staff_email").asText()).isEqualTo("studio@aakar.local"));
        JsonNode firstMove = mine.stream().filter(e -> e.get("action").asText().equals("order.advance")).reduce((a, b) -> b).orElseThrow(); // oldest
        assertThat(firstMove.get("before").get("status").asText()).isEqualTo("queued");
        assertThat(firstMove.get("after").get("status").asText()).isEqualTo("slicing");
        assertThat(firstMove.get("at").asText()).endsWith("Z");
    }

    @Test
    void ownersConfigureTheStudioAndTheStudioRoleIsReadOnlyThere() {
        String[] owner = bearer(body(post("/admin/api/auth/login", OWNER_LOGIN)).get("access_token").asText());
        PricingPolicy activeBefore = policies.active();
        String version = "test-admin-" + UUID.randomUUID().toString().substring(0, 8);
        String newMaterial = "test_material_" + UUID.randomUUID().toString().substring(0, 6).replace("-", "");
        String newSlug = "test-item-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode elephant = body(get("/api/catalog/items/elephant-bookends"));
        try {
            // pricing: history and active
            JsonNode history = body(get("/admin/api/pricing/policies", owner));
            assertThat(history.isArray()).isTrue();
            assertThat(history.findValuesAsText("version")).contains("2026-09-phase0");
            JsonNode active = body(get("/admin/api/pricing/policies/active", owner));
            assertThat(active.get("version").asText()).isEqualTo(activeBefore.version());
            assertThat(active.get("active").asBoolean()).isTrue();
            assertThat(active.get("policy").get("machine_rate_paise_per_hour").asLong()).isEqualTo(activeBefore.machineRatePaisePerHour());
            assertThat(active.get("policy").has("version")).isFalse();

            // publish: 201, then 409 on reuse and 422 on a bad body
            String policy = """
                    {"machine_rate_paise_per_hour": 25000, "finishing_fee_paise": {"matte": 8000, "silk": 12000}, "packaging_fee_paise": 0,
                     "margin_pct": 0, "round_to_rupees_ending_in": 9, "shipping_flat_paise": 7900, "free_shipping_above_paise": 99900,
                     "shipping_label": "Shipping · Delhivery, 4 days"}
                    """;
            ResponseEntity<String> published = post("/admin/api/pricing/policies",
                    "{\"version\": \"" + version + "\", \"note\": \"Machine rate up 25%\", \"policy\": " + policy + "}", owner);
            assertThat(published.getStatusCode()).as(published.getBody()).isEqualTo(HttpStatus.CREATED);
            JsonNode created = body(published);
            assertThat(created.get("version").asText()).isEqualTo(version);
            assertThat(created.get("active").asBoolean()).isTrue();
            assertThat(created.get("note").asText()).isEqualTo("Machine rate up 25%");
            assertThat(created.get("created_by").asText()).isEqualTo("studio@aakar.local");
            assertThat(created.get("policy").get("machine_rate_paise_per_hour").asLong()).isEqualTo(25000);
            assertThat(body(get("/admin/api/pricing/policies/active", owner)).get("version").asText()).isEqualTo(version);
            assertThat(policies.active().version()).isEqualTo(version);
            assertProblem(post("/admin/api/pricing/policies", "{\"version\": \"" + version + "\", \"policy\": " + policy + "}", owner),
                    HttpStatus.CONFLICT, "policy_version_exists");
            assertProblem(post("/admin/api/pricing/policies", "{\"version\": \"x\", \"policy\": " + policy + "}", owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/pricing/policies", "{\"version\": \"test-bad-body\", \"policy\": {\"margin_pct\": 900}}", owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/pricing/policies", "{\"version\": \"test-bad-body\"}", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(post("/admin/api/pricing/policies", "{\"version\": \"test-bad-body\", \"policy\": " + policy.replace("12000", "-5") + "}", owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");

            // preview: the board example under the draft policy (₹296 + ₹917 + ₹120 = ₹1,333 → ₹1,339, free shipping)
            JsonNode preview = body(post("/admin/api/pricing/preview",
                    "{\"policy\": " + policy + ", \"material\": \"terracotta_silk\", \"extruded_volume_cm3\": 51.6, \"print_seconds\": 13200}", owner));
            assertThat(preview.get("policy_version").asText()).isEqualTo("preview");
            assertThat(preview.get("material_id").asText()).isEqualTo("terracotta_silk");
            assertThat(preview.get("mass_g").asDouble()).isEqualTo(63.98);
            assertThat(preview.get("subtotal_paise").asLong()).isEqualTo(133_900);
            assertThat(preview.get("shipping_paise").asLong()).isZero();
            assertThat(preview.get("total_paise").asLong()).isEqualTo(133_900);
            assertProblem(post("/admin/api/pricing/preview",
                    "{\"policy\": " + policy + ", \"material\": \"unobtainium\", \"extruded_volume_cm3\": 51.6, \"print_seconds\": 13200}", owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "unknown_material");
            assertProblem(post("/admin/api/pricing/preview", "{\"policy\": " + policy + ", \"material\": \"terracotta_silk\"}", owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");

            // materials: all six, pause one, it disappears from the storefront
            JsonNode materials = body(get("/admin/api/materials", owner));
            assertThat(materials).hasSize(6);
            assertThat(materials.findValues("available")).allSatisfy(a -> assertThat(a.asBoolean()).isTrue());
            JsonNode indigo = materials.findParents("id").stream().filter(m -> m.get("id").asText().equals("indigo_matte")).findFirst().orElseThrow();
            assertThat(indigo.get("sort_order").asInt()).isEqualTo(6);
            assertThat(indigo.get("updated_at").asText()).endsWith("Z");
            JsonNode paused = body(put("/admin/api/materials/indigo_matte", materialInput(indigo, false), owner));
            assertThat(paused.get("available").asBoolean()).isFalse();
            assertThat(paused.get("rate_per_g_paise").asLong()).isEqualTo(420);
            assertThat(body(get("/api/catalog/materials")).findValuesAsText("id")).hasSize(5).doesNotContain("indigo_matte");
            assertThat(body(get("/admin/api/materials", owner))).hasSize(6);
            ResponseEntity<String> createdMaterial = post("/admin/api/materials", """
                    {"id": "%s", "name": "Test Marble", "filament": "Matte PLA, marble", "density_g_cm3": 1.25, "finish_class": "matte",
                     "rate_per_g_paise": 450, "heat_safe": false, "available": true, "sort_order": 50,
                     "pbr": {"color": "#EEEEEE", "roughness": 0.6, "metalness": 0.0}}
                    """.formatted(newMaterial), owner);
            assertThat(createdMaterial.getStatusCode()).as(createdMaterial.getBody()).isEqualTo(HttpStatus.CREATED);
            assertThat(body(createdMaterial).get("id").asText()).isEqualTo(newMaterial);
            assertProblem(post("/admin/api/materials", "{\"id\": \"" + newMaterial + "\", \"name\": \"Dup\", \"filament\": \"x\", \"density_g_cm3\": 1.2, "
                    + "\"finish_class\": \"matte\", \"rate_per_g_paise\": 1, \"pbr\": {\"color\": \"#FFFFFF\", \"roughness\": 0.5, \"metalness\": 0}}", owner),
                    HttpStatus.CONFLICT, "material_exists");
            assertProblem(post("/admin/api/materials", "{\"id\": \"Bad Id\", \"name\": \"x\"}", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");
            assertProblem(put("/admin/api/materials/nope_material", materialInput(indigo, true).replace("indigo_matte", "nope_material"), owner),
                    HttpStatus.NOT_FOUND, "not_found");

            // catalog: all six, update one, create one, 409 on the slug
            JsonNode items = body(get("/admin/api/catalog/items", owner));
            assertThat(items).hasSize(6);
            ((com.fasterxml.jackson.databind.node.ObjectNode) elephant).put("description", "A pair of elephant bookends, now with a test note.");
            JsonNode updated = body(put("/admin/api/catalog/items/elephant-bookends", elephant.toString(), owner));
            assertThat(updated.get("description").asText()).endsWith("test note.");
            assertThat(updated.get("available").asBoolean()).isFalse();
            assertThat(body(get("/api/catalog/items/elephant-bookends")).get("description").asText()).endsWith("test note.");
            ResponseEntity<String> createdItem = post("/admin/api/catalog/items", """
                    {"slug": "%s", "name": "Test Diya Holder", "category": "home_decor", "description": "A test item.", "template_id": "diya_holder",
                     "default_params": {"diameter_mm": 80}, "default_material": "terracotta_matte", "base_price_paise": 39900,
                     "specs_line": "80 mm · 40 g", "environment": "studio", "available": false, "media": []}
                    """.formatted(newSlug), owner);
            assertThat(createdItem.getStatusCode()).as(createdItem.getBody()).isEqualTo(HttpStatus.CREATED);
            assertThat(body(get("/admin/api/catalog/items", owner))).hasSize(7);
            assertProblem(post("/admin/api/catalog/items", createdItem.getBody()), HttpStatus.UNAUTHORIZED, "unauthenticated");
            assertProblem(post("/admin/api/catalog/items", body(createdItem).toString(), owner), HttpStatus.CONFLICT, "slug_exists");
            assertProblem(post("/admin/api/catalog/items", body(createdItem).toString().replace(newSlug, "other-slug-x").replace("terracotta_matte", "unobtainium"), owner),
                    HttpStatus.UNPROCESSABLE_ENTITY, "unknown_material");
            assertProblem(put("/admin/api/catalog/items/nope-item", body(createdItem).toString().replace(newSlug, "nope-item"), owner), HttpStatus.NOT_FOUND, "not_found");
            assertProblem(post("/admin/api/catalog/items", "{\"slug\": \"x\"}", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");

            // templates: live by default; switched off it leaves the storefront and blocks new designs
            JsonNode templates = body(get("/admin/api/templates", owner));
            assertThat(templates).hasSize(3); // jharokha_phone_stand, keychain_tag, raw_print (fixtures/templates.json)
            JsonNode jharokhaRow = templates.findParents("id").stream().filter(t -> t.get("id").asText().equals("jharokha_phone_stand")).findFirst().orElseThrow();
            assertThat(jharokhaRow.get("version").asInt()).isEqualTo(1);
            assertThat(jharokhaRow.get("family").asText()).isEqualTo("phone_stand");
            assertThat(jharokhaRow.get("live").asBoolean()).isTrue();
            assertThat(jharokhaRow.get("catalog_items")).extracting(JsonNode::asText).containsExactly("jharokha-phone-stand");
            JsonNode keychainRow = templates.findParents("id").stream().filter(t -> t.get("id").asText().equals("keychain_tag")).findFirst().orElseThrow();
            assertThat(keychainRow.get("catalog_items")).isEmpty();
            // the portal shows each template's Chhaap types and bought-in hardware
            assertThat(keychainRow.get("features_supported")).extracting(JsonNode::asText).contains("relief_image");
            assertThat(keychainRow.get("hardware").get(0).get("sku").asText()).isEqualTo("split_ring_25");
            assertThat(keychainRow.get("hardware").get(0).get("qty").asInt()).isEqualTo(1);
            JsonNode hidden = body(put("/admin/api/templates/jharokha_phone_stand", "{\"live\": false}", owner));
            assertThat(hidden.get("live").asBoolean()).isFalse();
            assertThat(body(get("/api/templates"))).extracting(d -> d.get("id").asText()).containsExactly("keychain_tag", "raw_print");
            assertThat(get("/api/templates/jharokha_phone_stand").getStatusCode()).isEqualTo(HttpStatus.OK); // existing designs keep resolving
            assertProblem(post("/api/designs", "{\"source\": \"shop\", \"catalog_item_slug\": \"jharokha-phone-stand\"}", guest(UUID.randomUUID())),
                    HttpStatus.UNPROCESSABLE_ENTITY, "template_not_available");
            assertProblem(post("/api/designs", "{\"source\": \"remix\", \"template_id\": \"jharokha_phone_stand\"}", guest(UUID.randomUUID())),
                    HttpStatus.UNPROCESSABLE_ENTITY, "template_not_available");
            assertThat(body(get("/admin/api/templates", owner)).findParents("id").stream()
                    .filter(t -> t.get("id").asText().equals("jharokha_phone_stand")).findFirst().orElseThrow().get("live").asBoolean()).isFalse();
            assertThat(body(put("/admin/api/templates/jharokha_phone_stand", "{\"live\": true}", owner)).get("live").asBoolean()).isTrue();
            assertThat(body(get("/api/templates"))).hasSize(3);
            assertProblem(put("/admin/api/templates/nope_template", "{\"live\": false}", owner), HttpStatus.NOT_FOUND, "not_found");
            assertProblem(put("/admin/api/templates/jharokha_phone_stand", "{}", owner), HttpStatus.UNPROCESSABLE_ENTITY, "validation_failed");

            // the audit log lists every configuration change with before/after
            JsonNode audit = body(get("/admin/api/audit?size=200", owner));
            assertThat(audit.get("total").asInt()).isGreaterThanOrEqualTo(6);
            List<JsonNode> entries = audit.get("items").findParents("action");
            assertThat(entries).extracting(e -> e.get("action").asText())
                    .contains("pricing.publish", "material.update", "material.create", "catalog.update", "catalog.create", "template.live");
            JsonNode publish = entries.stream().filter(e -> e.get("action").asText().equals("pricing.publish") && e.get("target").asText().equals(version))
                    .findFirst().orElseThrow();
            assertThat(publish.get("before").get("version").asText()).isEqualTo(activeBefore.version());
            assertThat(publish.get("after").get("machine_rate_paise_per_hour").asLong()).isEqualTo(25000);
            assertThat(publish.get("staff_email").asText()).isEqualTo("studio@aakar.local");
            JsonNode pausedEntry = entries.stream().filter(e -> e.get("action").asText().equals("material.update") && e.get("target").asText().equals("indigo_matte"))
                    .findFirst().orElseThrow();
            assertThat(pausedEntry.get("before").get("available").asBoolean()).isTrue();
            assertThat(pausedEntry.get("after").get("available").asBoolean()).isFalse();
            JsonNode live = entries.stream().filter(e -> e.get("action").asText().equals("template.live")).findFirst().orElseThrow(); // newest: back to live
            assertThat(live.get("before").get("live").asBoolean()).isFalse();
            assertThat(live.get("after").get("live").asBoolean()).isTrue();
            JsonNode page = body(get("/admin/api/audit?page=0&size=2", owner));
            assertThat(page.get("items")).hasSize(2);
            assertThat(page.get("size").asInt()).isEqualTo(2);
            assertThat(body(get("/admin/api/audit?size=999", owner)).get("size").asInt()).isEqualTo(200);

            // the messages log pages across orders
            JsonNode messages = body(get("/admin/api/notifications?size=5", owner));
            assertThat(messages.get("size").asInt()).isEqualTo(5);
            assertThat(messages.get("items").size()).isLessThanOrEqualTo(5);
            assertThat(messages.get("total").asInt()).isGreaterThanOrEqualTo(messages.get("items").size());

            // a studio-role account: fulfilment yes, configuration no
            String karigarEmail = "karigar-" + UUID.randomUUID().toString().substring(0, 8) + "@aakar.local";
            staffAccounts.create(karigarEmail, "Karigar Desk", StaffRole.studio, "aakar-karigar");
            JsonNode karigarSession = body(post("/admin/api/auth/login", "{\"email\": \"" + karigarEmail + "\", \"password\": \"aakar-karigar\"}"));
            assertThat(karigarSession.get("staff").get("role").asText()).isEqualTo("studio");
            String[] karigar = bearer(karigarSession.get("access_token").asText());
            assertThat(get("/admin/api/dashboard", karigar).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get("/admin/api/pricing/policies", karigar).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get("/admin/api/materials", karigar).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertProblem(post("/admin/api/pricing/policies", "{\"version\": \"test-karigar\", \"policy\": " + policy + "}", karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(put("/admin/api/materials/indigo_matte", materialInput(indigo, true), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(post("/admin/api/materials", body(createdMaterial).toString(), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(put("/admin/api/catalog/items/elephant-bookends", elephant.toString(), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(post("/admin/api/catalog/items", body(createdItem).toString(), karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertProblem(put("/admin/api/templates/jharokha_phone_stand", "{\"live\": false}", karigar), HttpStatus.FORBIDDEN, "forbidden");
            assertThat(body(get("/api/templates"))).hasSize(3); // nothing changed
            assertThat(body(post("/admin/api/pricing/preview",
                    "{\"policy\": " + policy + ", \"material\": \"terracotta_silk\", \"extruded_volume_cm3\": 51.6, \"print_seconds\": 13200}", karigar))
                    .get("total_paise").asLong()).isEqualTo(133_900); // a preview changes nothing, so the studio may run it
            PaidOrder paid = paidOrder();
            JsonNode advanced = body(post("/admin/api/orders/" + paid.orderId() + "/advance", "{\"status\": \"slicing\", \"detail\": {\"printer_bay\": \"B1\"}}", karigar));
            assertThat(advanced.get("status").asText()).isEqualTo("slicing");
            JsonNode karigarAudit = body(get("/admin/api/audit?size=5", karigar)).get("items").get(0);
            assertThat(karigarAudit.get("staff_email").asText()).isEqualTo(karigarEmail);
            assertThat(karigarAudit.get("action").asText()).isEqualTo("order.advance");
            assertThat(karigarAudit.get("target").asText()).isEqualTo(paid.orderNumber());
        } finally {
            // leave the shared database as the other test classes expect it
            policies.publish(activeBefore.withVersion("test-admin-restore-" + UUID.randomUUID().toString().substring(0, 8)), "test");
            jdbc.update("update materials set available = true where id = 'indigo_matte'");
            jdbc.update("delete from materials where id = ?", newMaterial);
            jdbc.update("update catalog_items set description = ? where slug = 'elephant-bookends'",
                    "A pair of elephant bookends with a weighted cavity for sand or steel shot.");
            jdbc.update("delete from catalog_items where slug = ?", newSlug);
            jdbc.update("delete from template_flags where template_id = 'jharokha_phone_stand'");
        }
    }

    /** {@code POST /admin/api/orders/{id}/qc-photos} as multipart with a {@code file} part and an optional {@code note}. */
    private ResponseEntity<String> upload(String orderId, String filename, MediaType type, byte[] bytes, String note, String[] staff) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(type);
        form.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, fileHeaders));
        if (note != null) {
            form.add("note", note);
        }
        return api().post().uri("/admin/api/orders/" + orderId + "/qc-photos").header(staff[0], staff[1])
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toEntity(String.class);
    }

    /** An {@code AdminMaterialInput} body from an {@code AdminMaterial}, with {@code available} overridden. */
    private static String materialInput(JsonNode material, boolean available) {
        Map<String, Object> input = new LinkedHashMap<>();
        for (String key : List.of("id", "name", "filament", "density_g_cm3", "finish_class", "rate_per_g_paise", "heat_safe", "sort_order", "pbr")) {
            input.put(key, material.get(key));
        }
        input.put("available", available);
        return input.entrySet().stream().map(e -> "\"" + e.getKey() + "\": " + e.getValue())
                .reduce((a, b) -> a + ", " + b).map(s -> "{" + s + "}").orElse("{}");
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
}
