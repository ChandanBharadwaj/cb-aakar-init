package studio.aakar.api.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * WireMock stand-in for the geometry service: one descriptor ({@code fixtures/templates.json}) and a
 * {@code POST /v1/build} that answers with {@code packages/contracts/examples/design.completed.example.json},
 * with job_id / design_id / version_no rewritten from the request via response templating.
 */
public final class GeometryStub {

    public static final String EXAMPLE_JOB_ID = "6f1c1a2e-9d0b-4a8e-8c1e-2f3d4e5f6a7b";
    public static final String EXAMPLE_DESIGN_ID = "0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f";
    /** Builds asking for this many cusps are slowed down so tests can observe live progress. */
    public static final int SLOW_BUILD_CUSPS = 3;
    public static final int SLOW_BUILD_MS = 2500;
    /** Builds asking for this many cusps fail with {@code not_printable}. */
    public static final int FAILING_CUSPS = 7;

    private GeometryStub() {
    }

    public static WireMockServer start() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort().globalTemplating(true));
        server.start();
        stub(server);
        return server;
    }

    public static void stub(WireMockServer server) {
        server.resetAll();
        server.stubFor(get(urlEqualTo("/v1/templates"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(resource("/fixtures/templates.json"))));

        String completed = completedTemplate();
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(5)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(completed)));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(1)
                .withRequestBody(matchingJsonPath("$.spec.params[?(@.arch_cusps == " + SLOW_BUILD_CUSPS + ")]"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(SLOW_BUILD_MS).withHeader("Content-Type", "application/json").withBody(completed)));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(1)
                .withRequestBody(matchingJsonPath("$.spec.params[?(@.arch_cusps == " + FAILING_CUSPS + ")]"))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json").withBody("""
                        {"job_id": "{{jsonPath request.body '$.job_id'}}", "design_id": "{{jsonPath request.body '$.design_id'}}",
                         "code": "not_printable", "message": "Seven cusps leave the arch too thin to print.",
                         "detail": {"thinnest_wall_mm": 0.9}}
                        """)));
    }

    /** The contracts example with its ids replaced by templating expressions over the request body. */
    private static String completedTemplate() {
        Path example = Contracts.require(Contracts.contracts("examples/design.completed.example.json"));
        try {
            return Files.readString(example)
                    .replace(EXAMPLE_JOB_ID, "{{jsonPath request.body '$.job_id'}}")
                    .replace(EXAMPLE_DESIGN_ID, "{{jsonPath request.body '$.design_id'}}")
                    .replace("\"version_no\": 1", "\"version_no\": {{jsonPath request.body '$.version_no'}}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String resource(String path) {
        try (InputStream in = GeometryStub.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
