package studio.aakar.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Boots the API on a random port against the {@code aakar_test} database (schema recreated) with the
 * geometry service replaced by {@link GeometryStub}. Subclasses share one application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"direct", "test"})
@Import(TestFlywayConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final WireMockServer GEOMETRY = GeometryStub.start();
    protected static final String ADDRESS_JSON = """
            {"label": "Home", "name": "Asha Rao", "phone": "+919876543210", "line1": "12 MG Road", "line2": "Flat 4B",
             "city": "Bengaluru", "state": "Karnataka", "pincode": "%s"}
            """;

    @DynamicPropertySource
    static void geometryUrl(DynamicPropertyRegistry registry) {
        registry.add("aakar.geometry.url", GEOMETRY::baseUrl);
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper json;

    private RestClient client;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /** A client that never throws on 4xx/5xx, so tests can assert Problem Details. */
    protected RestClient api() {
        if (client == null) {
            client = RestClient.builder()
                    .baseUrl(baseUrl())
                    .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
                    .build();
        }
        return client;
    }

    protected ResponseEntity<String> get(String path, String... headers) {
        return send(HttpMethod.GET, path, null, headers);
    }

    protected ResponseEntity<String> post(String path, String body, String... headers) {
        return send(HttpMethod.POST, path, body, headers);
    }

    protected ResponseEntity<String> patch(String path, String body, String... headers) {
        return send(HttpMethod.PATCH, path, body, headers);
    }

    protected ResponseEntity<String> put(String path, String body, String... headers) {
        return send(HttpMethod.PUT, path, body, headers);
    }

    protected ResponseEntity<String> delete(String path, String... headers) {
        return send(HttpMethod.DELETE, path, null, headers);
    }

    /** @param headers alternating name/value pairs, e.g. from {@link #bearer(String)} or {@link #guest(UUID)} */
    protected ResponseEntity<String> send(HttpMethod method, String path, String body, String... headers) {
        if (headers.length % 2 != 0) {
            throw new IllegalArgumentException("headers must be name/value pairs");
        }
        RestClient.RequestBodySpec request = api().method(method).uri(path);
        for (int i = 0; i < headers.length; i += 2) {
            request = request.header(headers[i], headers[i + 1]);
        }
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON);
            return request.body(body).retrieve().toEntity(String.class);
        }
        return request.retrieve().toEntity(String.class);
    }

    protected static String[] bearer(String token) {
        return new String[] {"Authorization", "Bearer " + token};
    }

    protected static String[] guest(UUID guestId) {
        return new String[] {"X-Aakar-Guest", guestId.toString()};
    }

    /** GET a binary body (zip, PDF) without throwing on 4xx/5xx. */
    protected ResponseEntity<byte[]> getBytes(String path, String... headers) {
        RestClient.RequestBodySpec request = api().method(HttpMethod.GET).uri(path);
        for (int i = 0; i + 1 < headers.length; i += 2) {
            request = request.header(headers[i], headers[i + 1]);
        }
        return request.retrieve().toEntity(byte[].class);
    }

    /** {@code POST /api/uploads} as multipart {@code file} + {@code kind}, never throwing on 4xx/5xx. */
    protected ResponseEntity<String> upload(String[] identity, String filename, String kind, byte[] bytes) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        form.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, fileHeaders));
        if (kind != null) {
            form.add("kind", kind);
        }
        RestClient.RequestBodySpec request = api().post().uri("/api/uploads");
        for (int i = 0; i + 1 < identity.length; i += 2) {
            request = request.header(identity[i], identity[i + 1]);
        }
        return request.contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toEntity(String.class);
    }

    /** Uploads a file that must be accepted (201) and returns the {@code Upload}. */
    protected JsonNode uploaded(String[] identity, String filename, String kind, byte[] bytes) {
        ResponseEntity<String> response = upload(identity, filename, kind, bytes);
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.CREATED);
        return body(response);
    }

    /** The seeded owner's staff bearer header. */
    protected String[] staffOwner() {
        ResponseEntity<String> login = post("/admin/api/auth/login", "{\"email\": \"studio@aakar.local\", \"password\": \"aakar-studio\"}");
        assertThat(login.getStatusCode()).as(login.getBody()).isEqualTo(HttpStatus.OK);
        return bearer(body(login).get("access_token").asText());
    }

    protected JsonNode body(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody() == null ? "null" : response.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- customer-loop helpers shared by the integration tests ------------------------------------------------------

    /** Signs a customer in through the mock OTP and returns the access token. */
    protected String signIn(String phone) {
        JsonNode otp = body(post("/api/auth/otp/request", "{\"phone\": \"" + phone + "\"}"));
        ResponseEntity<String> verified = post("/api/auth/otp/verify",
                "{\"request_id\": \"" + otp.get("request_id").asText() + "\", \"code\": \"" + otp.get("dev_code").asText() + "\"}");
        assertThat(verified.getStatusCode()).as(verified.getBody()).isEqualTo(HttpStatus.OK);
        return body(verified).get("access_token").asText();
    }

    /** Creates a design as the given identity (5 cusps = the Shop item) and returns its ready latest version. */
    protected JsonNode readyVersion(String[] identity, int cusps) {
        ResponseEntity<String> accepted = post("/api/designs", cusps == 5
                ? "{\"source\": \"shop\", \"catalog_item_slug\": \"jharokha-phone-stand\"}"
                : "{\"source\": \"remix\", \"template_id\": \"jharokha_phone_stand\", \"params\": {\"arch_cusps\": " + cusps + "}}", identity);
        assertThat(accepted.getStatusCode()).as(accepted.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        JsonNode a = body(accepted);
        awaitJob(a.get("job_id").asText(), "succeeded");
        JsonNode design = body(get("/api/designs/" + a.get("design_id").asText()));
        assertThat(design.get("status").asText()).isEqualTo("ready");
        return design.get("latest_version");
    }

    protected void awaitJob(String jobId, String status) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(150))
                .untilAsserted(() -> assertThat(body(get("/api/jobs/" + jobId)).get("status").asText()).isEqualTo(status));
    }

    protected static String cartItem(String versionId, String material, int qty) {
        return "{\"version_id\": \"" + versionId + "\", \"material\": \"" + material + "\", \"qty\": " + qty + "}";
    }

    /** A fresh Indian mobile per test so users never collide across runs. */
    protected static String phone() {
        return "+919" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    /** The whole customer loop up to a paid, queued order: sign-in, Shop design, cart, address, checkout, mock payment. */
    protected PaidOrder paidOrder() {
        String phone = phone();
        String token = signIn(phone);
        String userId = body(get("/api/auth/me", bearer(token))).get("id").asText();
        JsonNode version = readyVersion(bearer(token), 5);
        assertThat(post("/api/cart/items", cartItem(version.get("id").asText(), "terracotta_silk", 1), bearer(token)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        String addressId = body(post("/api/me/addresses", ADDRESS_JSON.formatted("560001"), bearer(token))).get("id").asText();
        ResponseEntity<String> checkedOut = post("/api/checkout", "{\"address_id\": \"" + addressId + "\", \"note\": \"Gift wrap please\"}", bearer(token));
        assertThat(checkedOut.getStatusCode()).as(checkedOut.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode checkout = body(checkedOut);
        ResponseEntity<String> paid = post("/api/payments/" + checkout.get("payment").get("id").asText() + "/mock/complete",
                "{\"outcome\": \"success\", \"method\": \"upi\"}", bearer(token));
        assertThat(paid.getStatusCode()).as(paid.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(body(get("/api/orders/" + checkout.get("order_id").asText(), bearer(token))).get("status").asText()).isEqualTo("queued");
        return new PaidOrder(token, phone, userId, checkout.get("order_id").asText(), checkout.get("order_number").asText(),
                version.get("design_id").asText(), version.get("id").asText());
    }

    /** What {@link #paidOrder()} leaves behind. */
    public record PaidOrder(String token, String phone, String userId, String orderId, String orderNumber, String designId, String versionId) {
    }

    protected JsonNode assertProblem(ResponseEntity<String> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).as("status for %s", response.getBody()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).as(response.getBody()).isTrue();
        JsonNode problem = body(response);
        assertThat(problem.get("code").asText()).as(response.getBody()).isEqualTo(code);
        assertThat(problem.get("status").asInt()).isEqualTo(status.value());
        assertThat(problem.get("title").asText()).isNotBlank();
        assertThat(problem.get("detail").asText()).isNotBlank();
        return problem;
    }
}
