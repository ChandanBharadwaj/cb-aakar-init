package studio.aakar.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * {@code V11__experiences_environments.sql} must not drift from {@code packages/design-tokens/experiences.json}: every
 * environment (backdrop) and every experience row, JSONB columns included, with the ordered avatars and curated items; and
 * every {@code environment} column now references the environments table.
 */
class ExperiencesSeedTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seededEnvironmentsMatchDesignTokens() {
        JsonNode expected = tokens().get("environments");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, label, surface, preset_key, palette::text as palette, sort_order from environments order by sort_order, id");

        assertThat(rows).as("environment count").hasSize(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            JsonNode want = expected.get(i);
            Map<String, Object> row = rows.get(i);
            String id = want.get("id").asText();
            assertThat(row.get("id")).as("environment #%d", i).isEqualTo(id);
            for (String text : List.of("label", "surface", "preset_key")) {
                assertThat(row.get(text)).as("%s %s", id, text).isEqualTo(want.get(text).asText());
            }
            assertThat(((Number) row.get("sort_order")).intValue()).as("%s sort_order", id).isEqualTo(want.path("sort_order").asInt(100));
            assertJsonEquivalent(id + " palette", readJson((String) row.get("palette")), want.get("palette"));
        }
    }

    @Test
    void seededExperiencesMatchDesignTokens() {
        JsonNode tokens = tokens();
        JsonNode expected = tokens.get("experiences");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select id, codename, slug, title, tagline, description, environment, surface::text as surface, style,
                       motif_pack::text as motif_pack, collections::text as collections, season::text as season, available, sort_order
                from experiences order by sort_order, id
                """);

        assertThat(rows).extracting(r -> (String) r.get("id")).as("experience ids in display order")
                .containsExactlyElementsOf(ids(expected, "id"));
        for (int i = 0; i < expected.size(); i++) {
            JsonNode want = expected.get(i);
            Map<String, Object> row = rows.get(i);
            String id = want.get("id").asText();
            for (String text : List.of("codename", "slug", "title", "environment", "style")) {
                assertThat(row.get(text)).as("%s %s", id, text).isEqualTo(want.get(text).asText());
            }
            for (String optional : List.of("tagline", "description")) {
                assertThat(row.get(optional)).as("%s %s", id, optional).isEqualTo(want.hasNonNull(optional) ? want.get(optional).asText() : null);
            }
            assertThat(row.get("available")).as("%s available", id).isEqualTo(want.get("available").asBoolean());
            assertThat(((Number) row.get("sort_order")).intValue()).as("%s sort_order", id).isEqualTo(want.path("sort_order").asInt(100));
            assertJsonEquivalent(id + " surface", readJson((String) row.get("surface")), want.get("surface"));
            for (String array : List.of("motif_pack", "collections", "season")) {
                assertJsonEquivalent(id + " " + array, readJson((String) row.get(array)), want.has(array) ? want.get(array) : json.createArrayNode());
            }

            assertThat(jdbc.queryForList("select family_id from experience_avatars where experience_id = ? order by sort_order", String.class, id))
                    .as("%s avatars in order", id).containsExactlyElementsOf(ids(want.get("avatars"), null));
            assertThat(jdbc.queryForList("select catalog_item_slug from experience_items where experience_id = ? order by sort_order", String.class, id))
                    .as("%s items in order", id).containsExactlyElementsOf(want.has("items") ? ids(want.get("items"), null) : List.of());
        }

        // The seed is internally consistent: every experience sits in a seeded backdrop.
        List<String> environments = ids(tokens.get("environments"), "id");
        expected.forEach(x -> assertThat(environments).as("%s environment", x.get("id").asText()).contains(x.get("environment").asText()));
    }

    @Test
    void everyEnvironmentColumnReferencesTheTable() {
        Map<String, String> references = new LinkedHashMap<>();
        jdbc.queryForList("""
                select c.conrelid::regclass::text as source, c.confrelid::regclass::text as target
                from pg_constraint c
                where c.contype = 'f' and c.confrelid = 'environments'::regclass
                order by 1
                """).forEach(r -> references.put((String) r.get("source"), (String) r.get("target")));
        assertThat(references).containsOnly(
                Map.entry("catalog_items", "environments"),
                Map.entry("experiences", "environments"),
                Map.entry("template_families", "environments"));
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'designs' and column_name = 'experience_id'",
                Integer.class)).isEqualTo(1);
    }

    private static JsonNode tokens() {
        Path file = Contracts.require(Contracts.designTokens("experiences.json"));
        return Contracts.readJson(file);
    }

    /** The {@code key} of every element, or the elements themselves when {@code key} is null. */
    private static List<String> ids(JsonNode array, String key) {
        List<String> ids = new ArrayList<>();
        array.forEach(n -> ids.add(key == null ? n.asText() : n.get(key).asText()));
        return ids;
    }

    private JsonNode readJson(String text) {
        try {
            return text == null ? null : json.readTree(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Structural equality that ignores JSONB's number normalisation and key order; a JSON null matches SQL NULL. */
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
