package studio.aakar.api.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * WireMock stand-in for the geometry service: the descriptors of {@code fixtures/templates.json} and a
 * {@code POST /v1/build} that answers with {@code packages/contracts/examples/design.completed.example.json},
 * with job_id / design_id / version_no rewritten from the request via response templating and the asset URLs
 * pointed at this server, which serves placeholder {@code /assets/**} model files (for the print pack).
 *
 * <p>Outcome families (plan §9 row 5): builds whose {@code $.spec.template} is {@code keychain_tag@1},
 * {@code fridge_magnet@1} or {@code raw_print@1} complete with the request's own spec echoed back (features included),
 * the template's {@code hardware} (the magnet reports {@code magnet_count} magnets) and a small print estimate; a
 * {@code hero_mesh} ({@code $.spec.features[0].type}) whose source URL contains {@value #UNUSABLE_MARKER} fails with
 * {@code content_unusable}.
 */
public final class GeometryStub {

    public static final String EXAMPLE_JOB_ID = "6f1c1a2e-9d0b-4a8e-8c1e-2f3d4e5f6a7b";
    public static final String EXAMPLE_DESIGN_ID = "0b8e7c6d-5a4b-4c3d-9e2f-1a0b9c8d7e6f";
    /** Builds asking for this many cusps are slowed down so tests can observe live progress. */
    public static final int SLOW_BUILD_CUSPS = 3;
    public static final int SLOW_BUILD_MS = 2500;
    /** Builds asking for this many cusps fail with {@code not_printable}. */
    public static final int FAILING_CUSPS = 7;
    /** Builds asking for this many cusps succeed but the printability report has {@code passed: false}. */
    public static final int UNSTABLE_CUSPS = 4;
    /** Host of the asset URLs in the contracts example, replaced by this server's base URL. */
    public static final String EXAMPLE_ASSET_HOST = "http://localhost:8081";
    public static final String KEYCHAIN_TEMPLATE = "keychain_tag@1";
    public static final String MAGNET_TEMPLATE = "fridge_magnet@1";
    public static final String RAW_PRINT_TEMPLATE = "raw_print@1";
    /** A hero form whose model URL contains this cannot be repaired: 422 {@code content_unusable}. */
    public static final String UNUSABLE_MARKER = "broken";
    /** Carrier builds (keychain, magnet): 30 min and 4 cm³ of filament, small enough for the family minimum to apply. */
    public static final int CARRIER_PRINT_SECONDS = 1800;
    public static final double CARRIER_EXTRUDED_CM3 = 4.0;
    /** Raw print builds: 1 h 30 m and 20 cm³. */
    public static final int RAW_PRINT_SECONDS = 5400;
    public static final double RAW_EXTRUDED_CM3 = 20.0;
    public static final String STUB_3MF = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><model unit=\"millimeter\" xml:lang=\"en-US\"><resources/><build/></model>";
    public static final String STUB_STL = "solid aakar_stub\nendsolid aakar_stub\n";

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

        String completed = completedTemplate().replace(EXAMPLE_ASSET_HOST, server.baseUrl());
        server.stubFor(get(urlMatching("/assets/.*\\.3mf"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "model/3mf").withBody(STUB_3MF)));
        server.stubFor(get(urlMatching("/assets/.*\\.stl"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "model/stl").withBody(STUB_STL)));
        server.stubFor(get(urlMatching("/assets/.*\\.glb"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "model/gltf-binary").withBody("glTF")));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(5)
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(completed)));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(1)
                .withRequestBody(matchingJsonPath("$.spec.params[?(@.arch_cusps == " + SLOW_BUILD_CUSPS + ")]"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(SLOW_BUILD_MS).withHeader("Content-Type", "application/json").withBody(completed)));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(1)
                .withRequestBody(matchingJsonPath("$.spec.params[?(@.arch_cusps == " + UNSTABLE_CUSPS + ")]"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(completed.replace("\"passed\": true", "\"passed\": false"))));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(3)
                .withRequestBody(matchingJsonPath("$.spec.template", equalTo(KEYCHAIN_TEMPLATE)))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(featureCompleted(server.baseUrl(),
                        "keychain_tag", "[{\"sku\": \"split_ring_25\", \"qty\": 1}]", CARRIER_PRINT_SECONDS, CARRIER_EXTRUDED_CM3, "[45, 27, 3.6]",
                        "A rounded Saathi tag, 45 mm long, with your photo in relief and a loop for the steel ring."))));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(3)
                .withRequestBody(matchingJsonPath("$.spec.template", equalTo(MAGNET_TEMPLATE)))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(featureCompleted(server.baseUrl(),
                        "fridge_magnet", "[{\"sku\": \"magnet_d10x3\", \"qty\": {{jsonPath request.body '$.spec.params.magnet_count'}} }]", // the space: "}}}" would close a triple-stash
                        CARRIER_PRINT_SECONDS, CARRIER_EXTRUDED_CM3, "[55, 41, 5]",
                        "A Chumbak plate with your photo in relief and pockets for the magnets in the back."))));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(3)
                .withRequestBody(matchingJsonPath("$.spec.template", equalTo(RAW_PRINT_TEMPLATE)))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(featureCompleted(server.baseUrl(),
                        "raw_print", "[]", RAW_PRINT_SECONDS, RAW_EXTRUDED_CM3, "[80, 52, 34]",
                        "Printed as it is from your own model file, sized to the longest side you chose."))));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(2)
                .withRequestBody(matchingJsonPath("$.spec.features[0].type", equalTo("hero_mesh")))
                .withRequestBody(matchingJsonPath("$.spec.features[0].source.url", containing(UNUSABLE_MARKER)))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json").withBody("""
                        {"job_id": "{{jsonPath request.body '$.job_id'}}", "design_id": "{{jsonPath request.body '$.design_id'}}",
                         "code": "content_unusable", "message": "We couldn't repair this model file into a solid we can print.",
                         "detail": {"reason": "not watertight after repair"}}
                        """)));
        server.stubFor(post(urlEqualTo("/v1/build"))
                .atPriority(1)
                .withRequestBody(matchingJsonPath("$.spec.params[?(@.arch_cusps == " + FAILING_CUSPS + ")]"))
                .willReturn(aResponse().withStatus(422).withHeader("Content-Type", "application/json").withBody("""
                        {"job_id": "{{jsonPath request.body '$.job_id'}}", "design_id": "{{jsonPath request.body '$.design_id'}}",
                         "code": "not_printable", "message": "Seven cusps leave the arch too thin to print.",
                         "detail": {"thinnest_wall_mm": 0.9}}
                        """)));
    }

    /**
     * A {@code design.completed} body for a template with content: the request's spec echoed back (the stub never
     * normalises), the given hardware, assets on this server, a passing printability report and a print estimate.
     */
    private static String featureCompleted(String baseUrl, String templateId, String hardware, int printSeconds, double extrudedCm3, String bounds,
            String note) {
        String key = "designs/{{jsonPath request.body '$.design_id'}}/v{{jsonPath request.body '$.version_no'}}";
        return """
                {"job_id": "{{jsonPath request.body '$.job_id'}}", "design_id": "{{jsonPath request.body '$.design_id'}}",
                 "version_no": {{jsonPath request.body '$.version_no'}},
                 "template": {"id": "%1$s", "version": 1},
                 "spec": {{jsonPath request.body '$.spec'}},
                 "hardware": %2$s,
                 "assets": {
                   "glb": {"key": "%3$s/model.glb", "url": "%4$s/assets/%3$s/model.glb", "bytes": 20480, "content_type": "model/gltf-binary"},
                   "3mf": {"key": "%3$s/model.3mf", "url": "%4$s/assets/%3$s/model.3mf", "bytes": 16384, "content_type": "model/3mf"},
                   "stl": {"key": "%3$s/model.stl", "url": "%4$s/assets/%3$s/model.stl", "bytes": 40960, "content_type": "model/stl"}
                 },
                 "printability": {
                   "report_version": "1.0", "passed": true,
                   "geometry": {"bounds_mm": %5$s, "volume_cm3": 3.1, "surface_cm2": 31.2, "triangles": 18400},
                   "checks": {
                     "manifold": {"status": "pass", "summary": "Watertight", "value": true},
                     "fits_bed": {"status": "pass", "summary": "Fits 250 × 250 × 250 mm bed", "value": true},
                     "thinnest_wall": {"status": "pass", "summary": "2 mm · safe", "value": 2.0, "unit": "mm", "threshold": 1.2},
                     "centre_of_gravity": {"status": "pass", "summary": "Inside base · 1 mm", "value": 1.0, "unit": "mm"},
                     "tipping_margin": {"status": "pass", "summary": "Stable", "value": 12.0, "unit": "mm", "threshold": 5}
                   },
                   "check_duration_ms": 400
                 },
                 "print_estimate": {"method": "heuristic", "print_seconds": %6$d, "extruded_volume_cm3": %7$s, "layer_height_mm": 0.2,
                                    "infill_pct": 15, "layers": 18, "supports_required": false},
                 "karigar_note": "%8$s",
                 "build_ms": 900}
                """.formatted(templateId, hardware, key, baseUrl, bounds, printSeconds, String.valueOf(extrudedCm3), note);
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
