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

    protected ResponseEntity<String> get(String path) {
        return api().get().uri(path).retrieve().toEntity(String.class);
    }

    protected ResponseEntity<String> post(String path, String body) {
        return api().post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(String.class);
    }

    protected JsonNode body(ResponseEntity<String> response) {
        try {
            return json.readTree(response.getBody() == null ? "null" : response.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
