package studio.aakar.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

    protected JsonNode body(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody() == null ? "null" : response.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
