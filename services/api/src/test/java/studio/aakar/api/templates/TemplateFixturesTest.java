package studio.aakar.api.templates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import studio.aakar.api.support.Contracts;

/**
 * {@code fixtures/templates.json} (what the WireMock geometry stub publishes) must not drift from the live descriptors the
 * geometry service exports to {@code packages/contracts/examples/template-descriptors.json}. The Jharokha fixture predates
 * that file and is kept as it is.
 */
class TemplateFixturesTest {

    static final String KEPT_AS_IS = "jharokha_phone_stand";

    @Test
    void carrierAndRawFixturesEqualTheExportedDescriptors() throws IOException {
        Path exported = Contracts.require(Contracts.contracts("examples/template-descriptors.json"));
        Map<String, JsonNode> live = byId(Contracts.readJson(exported));
        Map<String, JsonNode> fixtures;
        try (InputStream in = TemplateFixturesTest.class.getResourceAsStream("/fixtures/templates.json")) {
            fixtures = byId(new ObjectMapper().readTree(in));
        }

        assertThat(fixtures).containsKey(KEPT_AS_IS);
        assertThat(fixtures.keySet()).containsAll(live.keySet());
        live.forEach((id, descriptor) -> {
            if (!id.equals(KEPT_AS_IS)) {
                assertThat(fixtures.get(id)).as("fixture %s drifted from %s; copy it over", id, exported).isEqualTo(descriptor);
            }
        });
    }

    private static Map<String, JsonNode> byId(JsonNode array) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        array.forEach(d -> byId.put(d.get("id").asText(), d));
        return byId;
    }
}
