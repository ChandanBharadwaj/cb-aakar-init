package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.Contracts;

/** {@code V3__seed_materials.sql} must not drift from {@code packages/design-tokens/materials.json}. */
class MaterialsSeedTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seededMaterialsMatchDesignTokens() {
        Path file = Contracts.require(Contracts.designTokens("materials.json"));
        JsonNode expected = Contracts.readJson(file).get("materials");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, name, filament, density_g_cm3, finish_class, rate_per_g_paise, heat_safe, pbr::text as pbr from materials order by sort_order, id");

        assertThat(rows).as("row count").hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            JsonNode want = expected.get(i);
            Map<String, Object> row = rows.get(i);
            String id = want.get("id").asText();
            assertThat(row.get("id")).as("id #%d", i).isEqualTo(id);
            assertThat(row.get("name")).as("%s name", id).isEqualTo(want.get("name").asText());
            assertThat(row.get("filament")).as("%s filament", id).isEqualTo(want.get("filament").asText());
            assertThat(((Number) row.get("density_g_cm3")).doubleValue()).as("%s density", id).isCloseTo(want.get("density_g_cm3").asDouble(), within(1e-6));
            assertThat(row.get("finish_class")).as("%s finish_class", id).isEqualTo(want.get("finish_class").asText());
            assertThat(((Number) row.get("rate_per_g_paise")).longValue()).as("%s rate", id).isEqualTo(want.get("rate_per_g_paise").asLong());
            assertThat(row.get("heat_safe")).as("%s heat_safe", id).isEqualTo(want.get("heat_safe").asBoolean());

            JsonNode pbr = readJson((String) row.get("pbr"));
            JsonNode wantPbr = want.get("pbr");
            assertThat(fieldNames(pbr)).as("%s pbr keys", id).containsExactlyInAnyOrderElementsOf(fieldNames(wantPbr));
            wantPbr.properties().forEach(f -> {
                JsonNode got = pbr.get(f.getKey());
                if (f.getValue().isNumber()) {
                    assertThat(got.asDouble()).as("%s pbr.%s", id, f.getKey()).isCloseTo(f.getValue().asDouble(), within(1e-9));
                } else {
                    assertThat(got.asText()).as("%s pbr.%s", id, f.getKey()).isEqualTo(f.getValue().asText());
                }
            });
        }
    }

    @Test
    void pricingPolicyMatchesDesignTokens() {
        Path file = Contracts.require(Contracts.designTokens("materials.json"));
        JsonNode policy = Contracts.readJson(file).get("pricing_policy");
        JsonNode api = body(get("/actuator/health"));
        assertThat(api.get("status").asText()).isEqualTo("UP");

        var bound = calculatorPolicy();
        assertThat(bound.version()).isEqualTo(policy.get("version").asText());
        assertThat(bound.machineRatePaisePerHour()).isEqualTo(policy.get("machine_rate_paise_per_hour").asLong());
        assertThat(bound.finishingFeeFor("matte")).isEqualTo(policy.get("finishing_fee_paise").get("matte").asLong());
        assertThat(bound.finishingFeeFor("silk")).isEqualTo(policy.get("finishing_fee_paise").get("silk").asLong());
        assertThat(bound.packagingFeePaise()).isEqualTo(policy.get("packaging_fee_paise").asLong());
        assertThat(bound.marginPct()).isEqualTo(policy.get("margin_pct").asDouble());
        assertThat(bound.roundToRupeesEndingIn()).isEqualTo(policy.get("round_to_rupees_ending_in").asInt());
        assertThat(bound.shippingFlatPaise()).isEqualTo(policy.get("shipping_flat_paise").asLong());
        assertThat(bound.freeShippingAbovePaise()).isEqualTo(policy.get("free_shipping_above_paise").asLong());
        assertThat(bound.shippingLabel()).isEqualTo(policy.get("shipping_label").asText());
    }

    @Autowired
    studio.aakar.api.pricing.PricingPolicy pricingPolicy;

    private studio.aakar.api.pricing.PricingPolicy calculatorPolicy() {
        return pricingPolicy;
    }

    private JsonNode readJson(String text) {
        try {
            return json.readTree(text);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
