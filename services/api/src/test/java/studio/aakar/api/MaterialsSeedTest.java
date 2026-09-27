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

/**
 * {@code V3__seed_materials.sql}, the {@code aakar.pricing} seed and the seeded {@code pricing_policies} row for the tokens'
 * policy version ({@code V4} first, {@code V9} since the outcome families) must not drift from
 * {@code packages/design-tokens/materials.json} (ADR-0008: the tokens file is the seed).
 */
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
    void pricingPolicySeedMatchesDesignTokens() {
        Path file = Contracts.require(Contracts.designTokens("materials.json"));
        JsonNode policy = Contracts.readJson(file).get("pricing_policy");
        JsonNode api = body(get("/actuator/health"));
        assertThat(api.get("status").asText()).isEqualTo("UP");

        assertMatches("aakar.pricing", seed.toPolicy(), policy);
        var seeded = policies.byVersion(policy.get("version").asText());
        assertThat(seeded).as("seed row for %s", policy.get("version").asText()).isPresent();
        assertMatches("pricing_policies seed row", seeded.get(), policy);
        // The first seed (V4) stays in the history, deactivated by V9; policies published by other tests may be active now.
        assertThat(policies.byVersion("2026-09-phase0")).as("V4 seed row").isPresent();
        assertThat(policies.byVersion("2026-09-phase0").get().familyRules()).as("pre-Avatar policies read back without rules").isEmpty();
        assertThat(policies.byVersion("2026-09-phase0").get().hardwareMarkupPct()).isZero();
    }

    @Autowired
    studio.aakar.api.pricing.PricingPolicyProperties seed;
    @Autowired
    studio.aakar.api.pricing.PricingPolicyStore policies;

    private static void assertMatches(String what, studio.aakar.api.pricing.PricingPolicy bound, JsonNode policy) {
        assertThat(bound.version()).as(what).isEqualTo(policy.get("version").asText());
        assertThat(bound.machineRatePaisePerHour()).as(what).isEqualTo(policy.get("machine_rate_paise_per_hour").asLong());
        assertThat(bound.finishingFeeFor("matte")).as(what).isEqualTo(policy.get("finishing_fee_paise").get("matte").asLong());
        assertThat(bound.finishingFeeFor("silk")).as(what).isEqualTo(policy.get("finishing_fee_paise").get("silk").asLong());
        assertThat(bound.packagingFeePaise()).as(what).isEqualTo(policy.get("packaging_fee_paise").asLong());
        assertThat(bound.marginPct()).as(what).isEqualTo(policy.get("margin_pct").asDouble());
        assertThat(bound.roundToRupeesEndingIn()).as(what).isEqualTo(policy.get("round_to_rupees_ending_in").asInt());
        assertThat(bound.shippingFlatPaise()).as(what).isEqualTo(policy.get("shipping_flat_paise").asLong());
        assertThat(bound.freeShippingAbovePaise()).as(what).isEqualTo(policy.get("free_shipping_above_paise").asLong());
        assertThat(bound.shippingLabel()).as(what).isEqualTo(policy.get("shipping_label").asText());
        assertThat(bound.hardwareMarkupPct()).as("%s hardware_markup_pct", what).isEqualTo(policy.path("hardware_markup_pct").asDouble(0));
        JsonNode rules = policy.path("family_rules");
        assertThat(bound.familyRules().keySet()).as("%s family_rules", what).containsExactlyInAnyOrderElementsOf(fieldNames(rules));
        rules.properties().forEach(f -> {
            studio.aakar.api.pricing.PricingPolicy.FamilyRule rule = bound.familyRuleFor(f.getKey());
            assertThat(rule.minimumSubtotalPaise()).as("%s family_rules.%s.minimum_subtotal_paise", what, f.getKey())
                    .isEqualTo(f.getValue().path("minimum_subtotal_paise").asLong(0));
            assertThat(rule.setupFeePaise()).as("%s family_rules.%s.setup_fee_paise", what, f.getKey())
                    .isEqualTo(f.getValue().path("setup_fee_paise").asLong(0));
            assertThat(rule.qtyBreaks()).as("%s family_rules.%s.qty_breaks", what, f.getKey()).hasSize(f.getValue().path("qty_breaks").size());
        });
        assertThat(bound.familyRuleFor("no_such_family").isEmpty()).as("%s unknown family rule", what).isTrue();
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
