package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.order.Orders;
import studio.aakar.api.pricing.PricingPolicy;
import studio.aakar.api.pricing.PricingPolicyStore;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.GeometryStub;
import studio.aakar.api.support.SseClient;

/**
 * Phase 1 customer loop (ADR-0013) against Postgres with the geometry service stubbed: guest design → cart →
 * OTP sign-in attaching both → address → serviceability → checkout → mock payment → confirmed, queued order →
 * tracking SSE → studio stages to delivered. Plus the failure, authorisation and validation paths.
 */
class CustomerLoopIntegrationTest extends AbstractIntegrationTest {

    static final String ADDRESS = """
            {"label": "Home", "name": "Asha Rao", "phone": "+919876543210", "line1": "12 MG Road", "line2": "Flat 4B",
             "city": "Bengaluru", "state": "Karnataka", "pincode": "%s"}
            """;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Orders orders;
    @Autowired
    PricingPolicyStore policies;

    @Test
    void guestDesignAndCartBecomeAPaidOrderAfterSignIn() throws Exception {
        UUID guestId = UUID.randomUUID();
        String phone = phone();

        // 1. A guest starts a Shop design and adds the ready version to the cart.
        JsonNode version = readyVersion(guest(guestId), 5);
        String designId = version.get("design_id").asText();
        String versionId = version.get("id").asText();

        ResponseEntity<String> added = post("/api/cart/items", cartItem(versionId, "terracotta_silk", 1), guest(guestId));
        assertThat(added.getStatusCode()).as(added.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode guestCart = body(added);
        assertThat(guestCart.get("owner").asText()).isEqualTo("guest");
        assertThat(guestCart.get("items")).hasSize(1);
        JsonNode line = guestCart.get("items").get(0);
        assertThat(line.get("design_id").asText()).isEqualTo(designId);
        assertThat(line.get("version_no").asInt()).isEqualTo(1);
        assertThat(line.get("title").asText()).isEqualTo("Jharokha Phone Stand");
        assertThat(line.get("material_id").asText()).isEqualTo("terracotta_silk");
        assertThat(line.get("material_name").asText()).isEqualTo("Terracotta Silk");
        assertThat(line.get("specs_line").asText()).isEqualTo("Terracotta Silk · 92 × 78 × 120 mm · 64 g");
        assertThat(line.get("qty").asInt()).isEqualTo(1);
        assertThat(line.get("purchasable").asBoolean()).isTrue();
        assertThat(line.get("repriced").asBoolean()).isFalse();
        assertThat(line.get("unit_price").get("subtotal_paise").asLong()).isEqualTo(114_900);
        assertThat(line.get("line_total_paise").asLong()).isEqualTo(114_900);
        assertThat(guestCart.get("subtotal_paise").asLong()).isEqualTo(114_900);
        assertThat(guestCart.get("shipping_paise").asLong()).isZero();
        assertThat(guestCart.get("shipping_label").asText()).endsWith("· Free");
        assertThat(guestCart.get("total_paise").asLong()).isEqualTo(114_900);
        assertThat(guestCart.get("policy_version").asText()).isEqualTo(line.get("unit_price").get("policy_version").asText());
        assertThat(guestCart.get("updated_at").asText()).endsWith("Z");

        // 2. Sign in: the OTP mock hands the code back; verify attaches the guest's design and cart.
        ResponseEntity<String> requested = post("/api/auth/otp/request", "{\"phone\": \"" + phone + "\"}");
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode otp = body(requested);
        assertThat(otp.get("request_id").asText()).isNotBlank();
        assertThat(otp.get("expires_in_s").asInt()).isEqualTo(300);
        assertThat(otp.get("dev_code").asText()).matches("\\d{6}");

        ResponseEntity<String> verified = post("/api/auth/otp/verify",
                "{\"request_id\": \"" + otp.get("request_id").asText() + "\", \"code\": \"" + otp.get("dev_code").asText() + "\"}", guest(guestId));
        assertThat(verified.getStatusCode()).as(verified.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode session = body(verified);
        String token = session.get("access_token").asText();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(session.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(session.get("expires_in_s").asLong()).isEqualTo(7 * 24 * 3600);
        assertThat(session.get("user").get("phone").asText()).isEqualTo(phone);
        assertThat(session.get("user").get("id").asText()).isNotBlank();
        assertThat(session.get("user").get("name").isNull()).isTrue();
        assertThat(session.get("attached").get("designs").asInt()).isEqualTo(1);
        assertThat(session.get("attached").get("cart_items").asInt()).isEqualTo(1);
        String userId = session.get("user").get("id").asText();

        JsonNode me = body(get("/api/auth/me", bearer(token)));
        assertThat(me.get("phone").asText()).isEqualTo(phone);
        JsonNode userCart = body(get("/api/cart", bearer(token)));
        assertThat(userCart.get("owner").asText()).isEqualTo("user");
        assertThat(userCart.get("items")).hasSize(1);
        assertThat(userCart.get("items").get(0).get("version_id").asText()).isEqualTo(versionId);
        assertThat(body(get("/api/cart", guest(guestId))).get("items")).isEmpty(); // the guest cart is gone
        assertThat(get("/api/designs/" + designId).getStatusCode()).isEqualTo(HttpStatus.OK); // still readable by id
        assertThat(jdbc.queryForObject("select owner_id from designs where id = ?::uuid", String.class, designId)).isEqualTo(userId);
        assertThat(jdbc.queryForObject("select guest_id from designs where id = ?::uuid", String.class, designId)).isNull();

        // 3. Address and serviceability.
        ResponseEntity<String> created = post("/api/me/addresses", ADDRESS.formatted("560001"), bearer(token));
        assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode address = body(created);
        String addressId = address.get("id").asText();
        assertThat(address.get("is_default").asBoolean()).isTrue();
        assertThat(address.get("pincode").asText()).isEqualTo("560001");

        JsonNode serviceable = body(get("/api/shipping/serviceability?pincode=560001"));
        assertThat(serviceable.get("serviceable").asBoolean()).isTrue();
        assertThat(serviceable.get("carrier").asText()).isEqualTo("mock-delhivery");
        assertThat(serviceable.get("eta_days").asInt()).isEqualTo(4);
        assertThat(serviceable.get("cod_available").asBoolean()).isFalse();
        assertThat(body(get("/api/shipping/serviceability?pincode=900001")).get("serviceable").asBoolean()).isFalse();
        assertProblem(get("/api/shipping/serviceability?pincode=12"), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(get("/api/shipping/serviceability"), HttpStatus.BAD_REQUEST, "validation_failed");

        // 4. Checkout → order awaiting payment with a mock pay URL.
        ResponseEntity<String> checkedOut = post("/api/checkout", "{\"address_id\": \"" + addressId + "\", \"note\": \"Gift wrap please\"}", bearer(token));
        assertThat(checkedOut.getStatusCode()).as(checkedOut.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode result = body(checkedOut);
        String orderId = result.get("order_id").asText();
        String orderNumber = result.get("order_number").asText();
        assertThat(orderNumber).matches("AK-\\d{6}");
        JsonNode payment = result.get("payment");
        String paymentId = payment.get("id").asText();
        assertThat(payment.get("order_id").asText()).isEqualTo(orderId);
        assertThat(payment.get("gateway").asText()).isEqualTo("mock");
        assertThat(payment.get("gateway_ref").asText()).startsWith("mock_");
        assertThat(payment.get("status").asText()).isEqualTo("created");
        assertThat(payment.get("amount_paise").asLong()).isEqualTo(114_900);
        assertThat(payment.get("currency").asText()).isEqualTo("INR");
        assertThat(payment.get("pay_url").asText()).isEqualTo("http://localhost:3000/checkout/pay/" + paymentId);
        assertThat(payment.get("invoice_number").isNull()).isTrue();
        assertThat(payment.get("method").isNull()).isTrue();

        JsonNode pending = body(get("/api/orders/" + orderId, bearer(token)));
        assertThat(pending.get("number").asText()).isEqualTo(orderNumber);
        assertThat(pending.get("status").asText()).isEqualTo("pending_payment");
        assertThat(pending.get("stage").asText()).isEqualTo("payment");
        assertThat(pending.get("title").asText()).isEqualTo("Jharokha Phone Stand");
        assertThat(pending.get("items_count").asInt()).isEqualTo(1);
        assertThat(pending.get("subtotal_paise").asLong()).isEqualTo(114_900);
        assertThat(pending.get("shipping_paise").asLong()).isZero();
        assertThat(pending.get("total_paise").asLong()).isEqualTo(114_900);
        assertThat(pending.get("policy_version").asText()).isEqualTo(userCart.get("policy_version").asText());
        assertThat(pending.get("notify_whatsapp").asBoolean()).isTrue();
        assertThat(pending.get("note").asText()).isEqualTo("Gift wrap please");
        assertThat(LocalDate.parse(pending.get("eta").asText())).isAfter(LocalDate.now().plusDays(7)); // 4 transit + 5 production
        assertThat(pending.get("address").get("id").asText()).isEqualTo(addressId);
        assertThat(pending.get("address").get("pincode").asText()).isEqualTo("560001");
        assertThat(pending.get("address").get("is_default").asBoolean()).isTrue();
        JsonNode item = pending.get("items").get(0);
        assertThat(item.get("design_id").asText()).isEqualTo(designId);
        assertThat(item.get("version_id").asText()).isEqualTo(versionId);
        assertThat(item.get("specs_line").asText()).isEqualTo("Terracotta Silk · 92 × 78 × 120 mm · 64 g");
        assertThat(item.get("assets").get("glb").get("url").asText()).contains("/assets/designs/" + designId + "/v1/model.glb");
        assertThat(item.get("unit_price").get("total_paise").asLong()).isEqualTo(114_900);
        assertThat(item.get("line_total_paise").asLong()).isEqualTo(114_900);
        assertThat(pending.get("payment").get("id").asText()).isEqualTo(paymentId);
        assertThat(pending.get("shipment").isNull()).isTrue();
        assertThat(pending.get("events")).hasSize(1);
        assertThat(pending.get("events").get(0).get("sequence").asInt()).isEqualTo(1);
        assertThat(pending.get("events").get(0).get("message").asText()).isEqualTo("Awaiting payment");
        assertThat(pending.get("events").get(0).get("stage").asText()).isEqualTo("payment");
        assertThat(body(get("/api/payments/" + paymentId, bearer(token))).get("status").asText()).isEqualTo("created");
        // checkout consumes the cart; an unpaid order is paid from the order page
        assertThat(body(get("/api/cart", bearer(token))).get("items")).isEmpty(); // checkout consumed the cart

        // 5. The placeholder pay page reports success.
        ResponseEntity<String> completed = post("/api/payments/" + paymentId + "/mock/complete", "{\"outcome\": \"success\", \"method\": \"upi\"}", bearer(token));
        assertThat(completed.getStatusCode()).as(completed.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode paid = body(completed);
        assertThat(paid.get("status").asText()).isEqualTo("succeeded");
        assertThat(paid.get("method").asText()).isEqualTo("upi");
        assertThat(paid.get("invoice_number").asText()).matches("INV-\\d{4}-\\d{6}");
        assertThat(paid.get("finished_at").asText()).endsWith("Z");
        assertProblem(post("/api/payments/" + paymentId + "/mock/complete", "{\"outcome\": \"success\"}", bearer(token)), HttpStatus.CONFLICT, "payment_final");

        JsonNode queued = body(get("/api/orders/" + orderId, bearer(token)));
        assertThat(queued.get("status").asText()).isEqualTo("queued");
        assertThat(queued.get("stage").asText()).isEqualTo("queued");
        assertThat(queued.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(queued.get("payment").get("invoice_number").asText()).isEqualTo(paid.get("invoice_number").asText());
        assertThat(queued.get("events")).extracting(e -> e.get("message").asText())
                .containsExactly("Awaiting payment", "Order confirmed · payment received", "Queued at studio");
        assertThat(queued.get("events")).extracting(e -> e.get("status").asText()).containsExactly("pending_payment", "confirmed", "queued");
        assertThat(queued.get("events").get(1).get("detail").get("invoice_number").asText()).isEqualTo(paid.get("invoice_number").asText());
        assertThat(queued.get("events").get(2).get("detail").get("studio").asText()).isEqualTo("Bengaluru");
        assertThat(body(get("/api/cart", bearer(token))).get("items")).isEmpty(); // emptied by the PaymentSucceeded event
        assertProblem(post("/api/orders/" + orderId + "/payments", null, bearer(token)), HttpStatus.CONFLICT, "order_not_payable");

        List<Map<String, Object>> notifications = jdbc.queryForList(
                "select channel, template, \"to\", status, payload::text as payload from notifications where user_id = ?::uuid", userId);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0)).containsEntry("channel", "whatsapp").containsEntry("template", "order_confirmed")
                .containsEntry("to", phone).containsEntry("status", "logged");
        assertThat((String) notifications.get(0).get("payload")).contains(orderNumber).contains(paid.get("invoice_number").asText());

        // 6. Tracking stream: replay 1..3, then live events as the studio advances the order.
        try (SseClient sse = SseClient.open(baseUrl() + "/api/orders/" + orderId + "/events", null, bearer(token))) {
            assertThat(sse.status()).isEqualTo(200);
            assertThat(sse.contentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
            List<SseClient.Frame> replay = sse.read(3, Duration.ofSeconds(10));
            assertThat(replay).extracting(SseClient.Frame::id).containsExactly(1, 2, 3);
            assertThat(replay).extracting(SseClient.Frame::event).containsOnly("stage");
            assertThat(replay).extracting(f -> f.data().get("stage").asText()).containsExactly("payment", "queued", "queued");
            assertThat(replay.get(2).data().get("sequence").asInt()).isEqualTo(3);
            assertThat(replay.get(2).data().get("at").asText()).endsWith("Z");

            orders.advance(UUID.fromString(orderId), OrderStatus.slicing, null, Map.of("printer_bay", "B2"));
            List<SseClient.Frame> live = sse.read(1, Duration.ofSeconds(10));
            assertThat(live).singleElement().satisfies(f -> {
                assertThat(f.id()).isEqualTo(4);
                assertThat(f.data().get("status").asText()).isEqualTo("slicing");
                assertThat(f.data().get("message").asText()).isEqualTo("Slicing your design");
                assertThat(f.data().get("detail").get("printer_bay").asText()).isEqualTo("B2");
            });
        }
        try (SseClient resumed = SseClient.open(baseUrl() + "/api/orders/" + orderId + "/events", "2", bearer(token))) {
            assertThat(resumed.read(2, Duration.ofSeconds(10))).extracting(SseClient.Frame::id).containsExactly(3, 4);
        }

        // 7. Studio stages through to delivery: shipment booked at packed, tracked at shipped/delivered, stream closes.
        UUID oid = UUID.fromString(orderId);
        orders.advance(oid, OrderStatus.printing, "Layer 1 of 480", null);
        orders.advance(oid, OrderStatus.finishing, null, null);
        orders.advance(oid, OrderStatus.qc, null, null);
        assertThat(body(get("/api/orders/" + orderId, bearer(token))).get("stage").asText()).isEqualTo("sanding");
        orders.advance(oid, OrderStatus.packed, null, null);
        JsonNode packed = body(get("/api/orders/" + orderId, bearer(token)));
        assertThat(packed.get("shipment").get("carrier").asText()).isEqualTo("mock-delhivery");
        assertThat(packed.get("shipment").get("awb").asText()).matches("MOCK\\d{10}");
        assertThat(packed.get("shipment").get("status").asText()).isEqualTo("created");
        assertThat(packed.get("shipment").get("tracking_url").isNull()).isTrue();
        assertThat(packed.get("shipment").get("eta").asText()).isNotBlank();
        orders.advance(oid, OrderStatus.shipped, null, null);
        orders.advance(oid, OrderStatus.delivered, null, null);
        JsonNode delivered = body(get("/api/orders/" + orderId, bearer(token)));
        assertThat(delivered.get("status").asText()).isEqualTo("delivered");
        assertThat(delivered.get("shipment").get("status").asText()).isEqualTo("delivered");
        assertThat(delivered.get("shipment").get("events")).extracting(e -> e.get("status").asText()).containsExactly("created", "in_transit", "delivered");
        assertThat(delivered.get("events")).hasSize(10);
        try (SseClient closed = SseClient.open(baseUrl() + "/api/orders/" + orderId + "/events", "8", bearer(token))) {
            assertThat(closed.read(5, Duration.ofSeconds(10))).extracting(SseClient.Frame::id).containsExactly(9, 10); // then EOF
        }

        // 8. The orders list.
        JsonNode list = body(get("/api/orders", bearer(token)));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asText()).isEqualTo(orderId);
        assertThat(list.get(0).get("number").asText()).isEqualTo(orderNumber);
        assertThat(list.get(0).get("status").asText()).isEqualTo("delivered");
        assertThat(list.get(0).get("stage").asText()).isEqualTo("delivered");
        assertThat(list.get(0).get("items_count").asInt()).isEqualTo(1);
        assertThat(list.get(0).get("total_paise").asLong()).isEqualTo(114_900);
        assertThat(list.get(0).get("placed_at").asText()).endsWith("Z");

        // 9. Logout revokes the token.
        assertThat(post("/api/auth/logout", null, bearer(token)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertProblem(get("/api/orders", bearer(token)), HttpStatus.UNAUTHORIZED, "unauthenticated");
    }

    @Test
    void failedPaymentLeavesTheOrderPayableAndARetrySucceeds() {
        String token = signIn(phone());
        String versionId = readyVersion(bearer(token), 5).get("id").asText();
        assertThat(post("/api/cart/items", cartItem(versionId, "indigo_matte", 2), bearer(token)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String addressId = body(post("/api/me/addresses", ADDRESS.formatted("110001"), bearer(token))).get("id").asText();
        JsonNode checkout = body(post("/api/checkout", "{\"address_id\": \"" + addressId + "\", \"notify_whatsapp\": false}", bearer(token)));
        String orderId = checkout.get("order_id").asText();
        String firstPayment = checkout.get("payment").get("id").asText();
        assertThat(checkout.get("payment").get("amount_paise").asLong()).isEqualTo(2 * 108_900); // 64 g × ₹4.20 = ₹269 + ₹733 + ₹80 = 1082 → ₹1,089, twice

        JsonNode failed = body(post("/api/payments/" + firstPayment + "/mock/complete", "{\"outcome\": \"failure\", \"method\": \"card\"}", bearer(token)));
        assertThat(failed.get("status").asText()).isEqualTo("failed");
        assertThat(failed.get("method").asText()).isEqualTo("card");
        assertThat(failed.get("invoice_number").isNull()).isTrue();

        JsonNode order = body(get("/api/orders/" + orderId, bearer(token)));
        assertThat(order.get("status").asText()).isEqualTo("pending_payment");
        assertThat(order.get("events")).extracting(e -> e.get("message").asText()).containsExactly("Awaiting payment", "Payment failed");
        assertThat(order.get("events").get(1).get("stage").asText()).isEqualTo("payment");
        assertThat(order.get("payment").get("status").asText()).isEqualTo("failed");
        assertThat(body(get("/api/cart", bearer(token))).get("items")).isEmpty(); // checkout consumed the cart
        assertProblem(post("/api/checkout", "{\"address_id\": \"" + addressId + "\"}", bearer(token)), HttpStatus.CONFLICT, "cart_empty"); // no duplicate order
        assertProblem(post("/api/payments/" + firstPayment + "/mock/complete", "{\"outcome\": \"success\"}", bearer(token)), HttpStatus.CONFLICT, "payment_final");

        ResponseEntity<String> retried = post("/api/orders/" + orderId + "/payments", null, bearer(token));
        assertThat(retried.getStatusCode()).as(retried.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode second = body(retried);
        String secondPayment = second.get("id").asText();
        assertThat(secondPayment).isNotEqualTo(firstPayment);
        assertThat(second.get("status").asText()).isEqualTo("created");
        assertThat(second.get("pay_url").asText()).endsWith("/checkout/pay/" + secondPayment);
        assertThat(body(get("/api/orders/" + orderId, bearer(token))).get("payment").get("id").asText()).isEqualTo(secondPayment);

        JsonNode paid = body(post("/api/payments/" + secondPayment + "/mock/complete", "{\"outcome\": \"success\"}", bearer(token)));
        assertThat(paid.get("status").asText()).isEqualTo("succeeded");
        assertThat(paid.get("method").asText()).isEqualTo("upi"); // default
        JsonNode confirmed = body(get("/api/orders/" + orderId, bearer(token)));
        assertThat(confirmed.get("status").asText()).isEqualTo("queued");
        assertThat(confirmed.get("events")).extracting(e -> e.get("message").asText())
                .containsExactly("Awaiting payment", "Payment failed", "Order confirmed · payment received", "Queued at studio");
        assertThat(body(get("/api/cart", bearer(token))).get("items")).isEmpty();
        assertProblem(post("/api/orders/" + orderId + "/payments", null, bearer(token)), HttpStatus.CONFLICT, "order_not_payable");
        // with WhatsApp declined the confirmation is recorded on the SMS channel
        assertThat(jdbc.queryForList("select channel from notifications where payload->>'order_number' = ?", String.class, confirmed.get("number").asText()))
                .containsExactly("sms");
    }

    @Test
    void checkoutAndCartRefuseWhatCannotBeOrdered() {
        String token = signIn(phone());
        String far = body(post("/api/me/addresses", ADDRESS.formatted("900001"), bearer(token))).get("id").asText();

        // empty cart
        assertProblem(post("/api/checkout", "{\"address_id\": \"" + far + "\"}", bearer(token)), HttpStatus.CONFLICT, "cart_empty");

        // a ready version whose printability failed cannot even enter the cart
        String unstable = readyVersion(bearer(token), GeometryStub.UNSTABLE_CUSPS).get("id").asText();
        assertProblem(post("/api/cart/items", cartItem(unstable, "terracotta_silk", 1), bearer(token)), HttpStatus.CONFLICT, "not_printable");
        // a failed build is not ready
        JsonNode failed = body(post("/api/designs", """
                {"source": "remix", "template_id": "jharokha_phone_stand", "params": {"arch_cusps": %d}}
                """.formatted(GeometryStub.FAILING_CUSPS), bearer(token)));
        awaitJob(failed.get("job_id").asText(), "failed");
        String failedVersion = body(get("/api/designs/" + failed.get("design_id").asText())).get("latest_version").get("id").asText();
        assertProblem(post("/api/cart/items", cartItem(failedVersion, "terracotta_silk", 1), bearer(token)), HttpStatus.CONFLICT, "version_not_ready");
        // still generating
        JsonNode slow = body(post("/api/designs", """
                {"source": "remix", "template_id": "jharokha_phone_stand", "params": {"arch_cusps": %d}}
                """.formatted(GeometryStub.SLOW_BUILD_CUSPS), bearer(token)));
        String slowVersion = body(get("/api/designs/" + slow.get("design_id").asText())).get("latest_version").get("id").asText();
        assertProblem(post("/api/cart/items", cartItem(slowVersion, "terracotta_silk", 1), bearer(token)), HttpStatus.CONFLICT, "version_not_ready");
        awaitJob(slow.get("job_id").asText(), "succeeded");

        String ok = readyVersion(bearer(token), 5).get("id").asText();
        assertProblem(post("/api/cart/items", cartItem(ok, "unobtainium", 1), bearer(token)), HttpStatus.UNPROCESSABLE_ENTITY, "unknown_material");
        assertProblem(post("/api/cart/items", cartItem(UUID.randomUUID().toString(), "terracotta_silk", 1), bearer(token)), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(post("/api/cart/items", cartItem(ok, "terracotta_silk", 21), bearer(token)), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/cart/items", "{\"material\": \"terracotta_silk\"}", bearer(token)), HttpStatus.BAD_REQUEST, "validation_failed");
        assertThat(post("/api/cart/items", cartItem(ok, "terracotta_silk", 1), bearer(token)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // the address is not serviceable
        assertProblem(post("/api/checkout", "{\"address_id\": \"" + far + "\"}", bearer(token)), HttpStatus.UNPROCESSABLE_ENTITY, "not_serviceable");
        // someone else's (unknown) address
        assertProblem(post("/api/checkout", "{\"address_id\": \"" + UUID.randomUUID() + "\"}", bearer(token)), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(post("/api/checkout", "{}", bearer(token)), HttpStatus.BAD_REQUEST, "validation_failed");
    }

    @Test
    void cartLinesCombineChangeAndReprice() {
        UUID guestId = UUID.randomUUID();
        String versionId = readyVersion(guest(guestId), 5).get("id").asText();

        // no identity at all → 401
        assertProblem(get("/api/cart"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/api/cart", "X-Aakar-Guest", "not-a-uuid"), HttpStatus.BAD_REQUEST, "validation_failed");

        JsonNode cart = body(post("/api/cart/items", cartItem(versionId, "terracotta_silk", 2), guest(guestId)));
        assertThat(cart.get("items").get(0).get("qty").asInt()).isEqualTo(2);
        // same version + finish adds up, capped at 20
        cart = body(post("/api/cart/items", cartItem(versionId, "terracotta_silk", 19), guest(guestId)));
        assertThat(cart.get("items")).hasSize(1);
        assertThat(cart.get("items").get(0).get("qty").asInt()).isEqualTo(20);
        String itemId = cart.get("items").get(0).get("id").asText();
        // another finish is a second line
        cart = body(post("/api/cart/items", cartItem(versionId, "basic_white", 1), guest(guestId)));
        assertThat(cart.get("items")).hasSize(2);
        String whiteId = cart.get("items").get(1).get("id").asText();
        assertThat(cart.get("items").get(1).get("unit_price").get("subtotal_paise").asLong()).isEqualTo(105_900); // 64 g × ₹3.80 = ₹243 + ₹733 + ₹80 = 1056 → ₹1,059

        // PATCH qty, then switch the white line onto the silk finish → folded into that line
        cart = body(patch("/api/cart/items/" + itemId, "{\"qty\": 3}", guest(guestId)));
        assertThat(cart.get("items").get(0).get("qty").asInt()).isEqualTo(3);
        assertThat(cart.get("subtotal_paise").asLong()).isEqualTo(3 * 114_900 + 105_900);
        cart = body(patch("/api/cart/items/" + whiteId, "{\"material\": \"terracotta_silk\"}", guest(guestId)));
        assertThat(cart.get("items")).hasSize(1);
        assertThat(cart.get("items").get(0).get("qty").asInt()).isEqualTo(4);
        // PATCH onto a new finish re-prices the line
        cart = body(patch("/api/cart/items/" + itemId, "{\"material\": \"indigo_matte\", \"qty\": 1}", guest(guestId)));
        assertThat(cart.get("items").get(0).get("material_id").asText()).isEqualTo("indigo_matte");
        assertThat(cart.get("items").get(0).get("unit_price").get("subtotal_paise").asLong()).isEqualTo(108_900);
        assertThat(cart.get("items").get(0).get("specs_line").asText()).startsWith("Indigo Matte · ");
        assertProblem(patch("/api/cart/items/" + UUID.randomUUID(), "{\"qty\": 1}", guest(guestId)), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(patch("/api/cart/items/" + itemId, "{\"qty\": 0}", guest(guestId)), HttpStatus.BAD_REQUEST, "validation_failed");

        // A new pricing policy re-prices the line on the next read (ADR-0008), once.
        PricingPolicy active = policies.active();
        String bumped = "test-reprice-" + UUID.randomUUID();
        policies.publish(new PricingPolicy(bumped, active.machineRatePaisePerHour() * 2, active.finishingFeePaise(), active.packagingFeePaise(),
                active.marginPct(), active.roundToRupeesEndingIn(), active.shippingFlatPaise(), active.freeShippingAbovePaise(), active.shippingLabel()), "test");
        try {
            cart = body(get("/api/cart", guest(guestId)));
            assertThat(cart.get("policy_version").asText()).isEqualTo(bumped);
            JsonNode repriced = cart.get("items").get(0);
            assertThat(repriced.get("repriced").asBoolean()).isTrue();
            assertThat(repriced.get("unit_price").get("policy_version").asText()).isEqualTo(bumped);
            assertThat(repriced.get("unit_price").get("subtotal_paise").asLong()).isEqualTo(181_900); // ₹269 + ₹1,467 (3.67 h × ₹400) + ₹80 = 1816 → ₹1,819
            assertThat(body(get("/api/cart", guest(guestId))).get("items").get(0).get("repriced").asBoolean()).isFalse();
        } finally {
            policies.publish(active.withVersion("test-restore-" + UUID.randomUUID()), "test");
        }
        cart = body(get("/api/cart", guest(guestId)));
        assertThat(cart.get("items").get(0).get("repriced").asBoolean()).isTrue();
        assertThat(cart.get("items").get(0).get("unit_price").get("subtotal_paise").asLong()).isEqualTo(108_900);

        // DELETE a line, DELETE the cart
        cart = body(delete("/api/cart/items/" + itemId, guest(guestId)));
        assertThat(cart.get("items")).isEmpty();
        assertThat(cart.get("shipping_paise").asLong()).isZero();
        assertThat(cart.get("total_paise").asLong()).isZero();
        assertProblem(delete("/api/cart/items/" + itemId, guest(guestId)), HttpStatus.NOT_FOUND, "not_found");
        body(post("/api/cart/items", cartItem(versionId, "basic_white", 1), guest(guestId)));
        assertThat(body(delete("/api/cart", guest(guestId))).get("items")).isEmpty();
    }

    @Test
    void protectedRoutesNeedATokenAndOrdersAreOwnerOnly() {
        assertProblem(get("/api/orders"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(post("/api/checkout", "{\"address_id\": \"" + UUID.randomUUID() + "\"}"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/api/me/addresses"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/api/auth/me"), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/api/payments/" + UUID.randomUUID()), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/api/orders", bearer("garbage")), HttpStatus.UNAUTHORIZED, "unauthenticated");
        assertProblem(get("/api/cart", bearer("garbage")), HttpStatus.UNAUTHORIZED, "unauthenticated"); // a bad token is never a guest
        assertProblem(get("/api/orders", "X-Aakar-Guest", UUID.randomUUID().toString()), HttpStatus.UNAUTHORIZED, "unauthenticated");
        // public routes stay public
        assertThat(get("/api/catalog/items").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/actuator/health").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/v3/api-docs").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertProblem(get("/api/nowhere"), HttpStatus.NOT_FOUND, "not_found");

        String asha = signIn(phone());
        String ravi = signIn(phone());
        String versionId = readyVersion(bearer(asha), 5).get("id").asText();
        post("/api/cart/items", cartItem(versionId, "terracotta_silk", 1), bearer(asha));
        String addressId = body(post("/api/me/addresses", ADDRESS.formatted("400001"), bearer(asha))).get("id").asText();
        JsonNode checkout = body(post("/api/checkout", "{\"address_id\": \"" + addressId + "\"}", bearer(asha)));
        String orderId = checkout.get("order_id").asText();
        String paymentId = checkout.get("payment").get("id").asText();

        assertThat(get("/api/orders/" + orderId, bearer(asha)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertProblem(get("/api/orders/" + orderId, bearer(ravi)), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(get("/api/orders/" + orderId + "/events", bearer(ravi)), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(post("/api/orders/" + orderId + "/payments", null, bearer(ravi)), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(get("/api/payments/" + paymentId, bearer(ravi)), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(post("/api/payments/" + paymentId + "/mock/complete", "{\"outcome\": \"success\"}", bearer(ravi)), HttpStatus.NOT_FOUND, "not_found");
        assertProblem(put("/api/me/addresses/" + addressId, ADDRESS.formatted("400001"), bearer(ravi)), HttpStatus.NOT_FOUND, "not_found");
        assertThat(body(get("/api/orders", bearer(ravi)))).isEmpty();
        assertProblem(post("/api/checkout", "{\"address_id\": \"" + addressId + "\"}", bearer(ravi)), HttpStatus.CONFLICT, "cart_empty");
    }

    @Test
    void profileAndAddresses() {
        String token = signIn(phone());
        JsonNode me = body(patch("/api/auth/me", "{\"name\": \"Asha Rao\", \"email\": \"asha@example.com\"}", bearer(token)));
        assertThat(me.get("name").asText()).isEqualTo("Asha Rao");
        assertThat(me.get("email").asText()).isEqualTo("asha@example.com");
        assertProblem(patch("/api/auth/me", "{\"email\": \"nope\"}", bearer(token)), HttpStatus.BAD_REQUEST, "validation_failed");

        assertThat(body(get("/api/me/addresses", bearer(token)))).isEmpty();
        JsonNode home = body(post("/api/me/addresses", ADDRESS.formatted("560001"), bearer(token)));
        assertThat(home.get("is_default").asBoolean()).isTrue(); // first one
        assertThat(home.get("label").asText()).isEqualTo("Home");
        assertThat(home.get("line2").asText()).isEqualTo("Flat 4B");
        JsonNode office = body(post("/api/me/addresses", """
                {"label": "Office", "name": "Asha Rao", "phone": "+919876543210", "line1": "Tower 2, Manyata", "city": "Bengaluru", "state": "Karnataka", "pincode": "560045"}
                """, bearer(token)));
        assertThat(office.get("is_default").asBoolean()).isFalse();
        assertThat(office.get("line2").isNull()).isTrue();
        assertProblem(post("/api/me/addresses", "{\"name\": \"x\"}", bearer(token)), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/me/addresses", ADDRESS.formatted("0123"), bearer(token)), HttpStatus.BAD_REQUEST, "validation_failed");

        JsonNode list = body(get("/api/me/addresses", bearer(token)));
        assertThat(list).extracting(a -> a.get("label").asText()).containsExactly("Home", "Office"); // default first

        JsonNode updated = body(put("/api/me/addresses/" + office.get("id").asText(), """
                {"label": "Office", "name": "Asha Rao", "phone": "+919876543210", "line1": "Tower 3, Manyata", "city": "Bengaluru", "state": "Karnataka", "pincode": "560045", "is_default": true}
                """, bearer(token)));
        assertThat(updated.get("is_default").asBoolean()).isTrue();
        assertThat(updated.get("line1").asText()).isEqualTo("Tower 3, Manyata");
        list = body(get("/api/me/addresses", bearer(token)));
        assertThat(list.get(0).get("id").asText()).isEqualTo(office.get("id").asText());
        assertThat(list.get(1).get("is_default").asBoolean()).isFalse();

        assertThat(delete("/api/me/addresses/" + office.get("id").asText(), bearer(token)).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertProblem(delete("/api/me/addresses/" + office.get("id").asText(), bearer(token)), HttpStatus.NOT_FOUND, "not_found");
        list = body(get("/api/me/addresses", bearer(token)));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("is_default").asBoolean()).isTrue(); // promoted
    }

    @Test
    void otpRequestsAreValidatedAndRateLimited() {
        assertProblem(post("/api/auth/otp/request", "{\"phone\": \"9876543210\"}"), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/auth/otp/request", "{\"phone\": \"+911234567890\"}"), HttpStatus.BAD_REQUEST, "validation_failed");
        assertProblem(post("/api/auth/otp/request", "{}"), HttpStatus.BAD_REQUEST, "validation_failed");

        String phone = phone();
        JsonNode first = null;
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> ok = post("/api/auth/otp/request", "{\"phone\": \"" + phone + "\"}");
            assertThat(ok.getStatusCode()).as("request %d", i + 1).isEqualTo(HttpStatus.ACCEPTED);
            if (first == null) {
                first = body(ok);
            }
        }
        assertProblem(post("/api/auth/otp/request", "{\"phone\": \"" + phone + "\"}"), HttpStatus.TOO_MANY_REQUESTS, "otp_rate_limited");

        assertProblem(post("/api/auth/otp/verify", "{\"request_id\": \"" + first.get("request_id").asText() + "\", \"code\": \"000000\"}"),
                HttpStatus.UNAUTHORIZED, "otp_invalid");
        assertProblem(post("/api/auth/otp/verify", "{\"request_id\": \"" + UUID.randomUUID() + "\", \"code\": \"123456\"}"),
                HttpStatus.UNAUTHORIZED, "otp_invalid");
        assertProblem(post("/api/auth/otp/verify", "{\"request_id\": \"" + first.get("request_id").asText() + "\", \"code\": \"1\"}"),
                HttpStatus.BAD_REQUEST, "validation_failed");
        // the right code still works after a wrong attempt, and only once
        ResponseEntity<String> verified = post("/api/auth/otp/verify",
                "{\"request_id\": \"" + first.get("request_id").asText() + "\", \"code\": \"" + first.get("dev_code").asText() + "\"}");
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(verified).get("attached").get("designs").asInt()).isZero();
        assertProblem(post("/api/auth/otp/verify",
                "{\"request_id\": \"" + first.get("request_id").asText() + "\", \"code\": \"" + first.get("dev_code").asText() + "\"}"),
                HttpStatus.UNAUTHORIZED, "otp_invalid");
    }

    @Test
    void openApiDescribesTheNewPathsWithSnakeCase() {
        JsonNode docs = body(get("/v3/api-docs"));
        assertThat(docs.get("paths").fieldNames()).toIterable().contains("/api/auth/otp/request", "/api/auth/otp/verify", "/api/auth/me",
                "/api/auth/logout", "/api/me/addresses", "/api/me/addresses/{addressId}", "/api/cart", "/api/cart/items", "/api/cart/items/{itemId}",
                "/api/shipping/serviceability", "/api/checkout", "/api/orders", "/api/orders/{orderId}", "/api/orders/{orderId}/events",
                "/api/orders/{orderId}/payments", "/api/payments/{paymentId}", "/api/payments/{paymentId}/mock/complete");
        JsonNode cart = docs.get("components").get("schemas").get("CartDto").get("properties");
        assertThat(cart.fieldNames()).toIterable().contains("owner", "items", "subtotal_paise", "shipping_paise", "shipping_label", "total_paise", "policy_version");
        // the Identity parameter is resolved from headers, never documented as a query parameter
        JsonNode cartParams = docs.get("paths").get("/api/cart").get("get").get("parameters");
        if (cartParams != null) {
            assertThat(cartParams.findValuesAsText("name")).doesNotContain("identity", "kind", "id");
        }
        assertThat(docs.get("components").get("securitySchemes").get("bearer").get("scheme").asText()).isEqualTo("bearer");
    }
}
