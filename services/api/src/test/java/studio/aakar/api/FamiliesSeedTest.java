package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import studio.aakar.api.support.AbstractIntegrationTest;
import studio.aakar.api.support.Contracts;

/**
 * {@code V9__families_hardware_uploads.sql} must not drift from {@code packages/design-tokens/families.json}: the
 * shelves, the bought-in hardware and every outcome family (Avatar) row, JSONB columns included, plus the
 * {@code family_id} backfill of the six seeded Shop items.
 */
class FamiliesSeedTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seededShelvesMatchDesignTokens() {
        JsonNode expected = tokens().get("shelves");
        List<Map<String, Object>> rows = jdbc.queryForList("select id, label, sort_order from shelves order by sort_order, id");

        assertThat(rows).as("shelf count").hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            JsonNode want = expected.get(i);
            Map<String, Object> row = rows.get(i);
            String id = want.get("id").asText();
            assertThat(row.get("id")).as("shelf #%d", i).isEqualTo(id);
            assertThat(row.get("label")).as("%s label", id).isEqualTo(want.get("label").asText());
            assertThat(((Number) row.get("sort_order")).intValue()).as("%s sort_order", id).isEqualTo(want.path("sort_order").asInt(100));
        }
    }

    @Test
    void seededHardwareMatchesDesignTokens() {
        Map<String, JsonNode> expected = byKey(tokens().get("hardware_items"), "sku");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select sku, name, unit_cost_paise, weight_g, supplier, url, notes, available from hardware_items order by sku");

        assertThat(rows).extracting(r -> (String) r.get("sku")).as("skus").containsExactlyInAnyOrderElementsOf(expected.keySet());
        for (Map<String, Object> row : rows) {
            String sku = (String) row.get("sku");
            JsonNode want = expected.get(sku);
            assertThat(row.get("name")).as("%s name", sku).isEqualTo(want.get("name").asText());
            assertThat(((Number) row.get("unit_cost_paise")).longValue()).as("%s unit_cost_paise", sku).isEqualTo(want.get("unit_cost_paise").asLong());
            if (want.hasNonNull("weight_g")) {
                assertThat(((Number) row.get("weight_g")).doubleValue()).as("%s weight_g", sku).isCloseTo(want.get("weight_g").asDouble(), within(1e-6));
            } else {
                assertThat(row.get("weight_g")).as("%s weight_g", sku).isNull();
            }
            for (String text : List.of("supplier", "url", "notes")) {
                assertThat(row.get(text)).as("%s %s", sku, text).isEqualTo(want.hasNonNull(text) ? want.get(text).asText() : null);
            }
            assertThat(row.get("available")).as("%s available", sku).isEqualTo(want.path("available").asBoolean(true));
        }
    }

    @Test
    void seededFamiliesMatchDesignTokens() {
        JsonNode tokens = tokens();
        Map<String, JsonNode> expected = byKey(tokens.get("families"), "id");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, codename, name, tagline, description, kind, tier, shelf, demand_rank, default_template_id, environment,
                       size_envelope::text as size_envelope, hardware::text as hardware, material_rules::text as material_rules,
                       shape_tolerance, content_slot::text as content_slot, available, sort_order
                from template_families order by sort_order, id
                """);

        assertThat(rows).extracting(r -> (String) r.get("id")).as("family ids").containsExactlyInAnyOrderElementsOf(expected.keySet());
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("id");
            JsonNode want = expected.get(id);
            for (String text : List.of("codename", "name", "kind", "tier", "shelf", "default_template_id", "shape_tolerance")) {
                assertThat(row.get(text)).as("%s %s", id, text).isEqualTo(want.get(text).asText());
            }
            for (String optional : List.of("tagline", "description")) {
                assertThat(row.get(optional)).as("%s %s", id, optional).isEqualTo(want.hasNonNull(optional) ? want.get(optional).asText() : null);
            }
            assertThat(row.get("environment")).as("%s environment", id).isEqualTo(want.path("environment").asText("studio"));
            assertThat(row.get("demand_rank") == null ? null : ((Number) row.get("demand_rank")).intValue()).as("%s demand_rank", id)
                    .isEqualTo(want.hasNonNull("demand_rank") ? want.get("demand_rank").asInt() : null);
            assertThat(row.get("available")).as("%s available", id).isEqualTo(want.get("available").asBoolean());
            assertThat(((Number) row.get("sort_order")).intValue()).as("%s sort_order", id).isEqualTo(want.path("sort_order").asInt(100));

            assertJsonEquivalent(id + " size_envelope", readJson((String) row.get("size_envelope")), want.get("size_envelope_mm"));
            assertJsonEquivalent(id + " hardware", readJson((String) row.get("hardware")), want.has("hardware") ? want.get("hardware") : json.createArrayNode());
            assertJsonEquivalent(id + " material_rules", readJson((String) row.get("material_rules")),
                    want.has("material_rules") ? want.get("material_rules") : json.createObjectNode());
            assertJsonEquivalent(id + " content_slot", readJson((String) row.get("content_slot")), want.get("content_slot"));
        }

        // The seed is internally consistent: every family sits on a seeded shelf and packs seeded hardware.
        List<String> shelves = new ArrayList<>();
        tokens.get("shelves").forEach(s -> shelves.add(s.get("id").asText()));
        List<String> skus = new ArrayList<>();
        tokens.get("hardware_items").forEach(h -> skus.add(h.get("sku").asText()));
        expected.values().forEach(f -> {
            assertThat(shelves).as("%s shelf", f.get("id").asText()).contains(f.get("shelf").asText());
            f.path("hardware").forEach(h -> assertThat(skus).as("%s hardware", f.get("id").asText()).contains(h.get("sku").asText()));
        });
    }

    @Test
    void seededShopItemsNameTheirFamily() {
        Map<String, String> families = new LinkedHashMap<>();
        jdbc.queryForList("select slug, family_id from catalog_items where slug in ('jharokha-phone-stand', 'ajrakh-coasters', 'fluted-planter', "
                + "'kantha-nameplate', 'elephant-bookends', 'pillar-headphone-stand') order by slug")
                .forEach(r -> families.put((String) r.get("slug"), (String) r.get("family_id")));
        assertThat(families).containsOnly(
                Map.entry("jharokha-phone-stand", "phone_stand"),
                Map.entry("ajrakh-coasters", "coaster"),
                Map.entry("fluted-planter", "planter"),
                Map.entry("kantha-nameplate", "nameplate"),
                Map.entry("elephant-bookends", "bookend"),
                Map.entry("pillar-headphone-stand", "headphone_stand"));
    }

    private static JsonNode tokens() {
        Path file = Contracts.require(Contracts.designTokens("families.json"));
        return Contracts.readJson(file);
    }

    private static Map<String, JsonNode> byKey(JsonNode array, String key) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        array.forEach(n -> map.put(n.get(key).asText(), n));
        return map;
    }

    private JsonNode readJson(String text) {
        try {
            return text == null ? null : json.readTree(text);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Structural equality that ignores JSONB's number normalisation and key order. */
    private static void assertJsonEquivalent(String what, JsonNode got, JsonNode want) {
        if (want == null || want.isNull()) {
            assertThat(got == null || got.isNull()).as("%s should be null, got %s", what, got).isTrue();
            return;
        }
        assertThat(got).as(what).isNotNull();
        if (want.isObject()) {
            assertThat(got.isObject()).as("%s is an object", what).isTrue();
            assertThat(fieldNames(got)).as("%s keys", what).containsExactlyInAnyOrderElementsOf(fieldNames(want));
            want.properties().forEach(f -> assertJsonEquivalent(what + "." + f.getKey(), got.get(f.getKey()), f.getValue()));
        } else if (want.isArray()) {
            assertThat(got.isArray()).as("%s is an array", what).isTrue();
            assertThat(got.size()).as("%s length", what).isEqualTo(want.size());
            for (int i = 0; i < want.size(); i++) {
                assertJsonEquivalent(what + "[" + i + "]", got.get(i), want.get(i));
            }
        } else if (want.isNumber()) {
            assertThat(got.isNumber()).as("%s is a number", what).isTrue();
            assertThat(got.asDouble()).as(what).isCloseTo(want.asDouble(), within(1e-9));
        } else {
            assertThat(got).as(what).isEqualTo(want);
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
