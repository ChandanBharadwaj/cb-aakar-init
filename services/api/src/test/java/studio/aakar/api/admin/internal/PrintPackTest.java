package studio.aakar.api.admin.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.Designs;
import studio.aakar.api.design.VersionStatus;
import studio.aakar.api.order.OrderDto;
import studio.aakar.api.order.OrderItemDto;
import studio.aakar.api.order.OrderStage;
import studio.aakar.api.order.OrderStatus;
import studio.aakar.api.pricing.PriceBreakdown;

/** The zip holds a sheet and the model files per item; a file that cannot be fetched is noted, not fatal. */
class PrintPackTest {

    private final Designs designs = mock(Designs.class);
    private final Catalog catalog = mock(Catalog.class);

    @Test
    void buildsOneFolderPerItemAndNotesMissingFiles() throws IOException {
        UUID versionId = UUID.randomUUID();
        UUID designId = UUID.randomUUID();
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put("3mf", Map.of("url", "http://geometry/assets/model.3mf", "content_type", "model/3mf"));
        assets.put("stl", Map.of("url", "http://geometry/assets/model.stl", "content_type", "model/stl"));
        PriceBreakdown price = new PriceBreakdown("INR", "terracotta_silk", 63.98, 13_200, List.of(), 114_900, 0, "Free", 114_900, "2026-09-phase0");
        OrderItemDto item = new OrderItemDto(UUID.randomUUID(), designId, versionId, 1, "Jharokha Phone Stand", "Terracotta Silk · 92 × 78 × 120 mm · 64 g",
                "terracotta_silk", "Terracotta Silk", 2, price, 229_800, assets);
        OrderDto order = new OrderDto(UUID.randomUUID(), "AK-000007", OrderStatus.queued, OrderStage.queued, "Jharokha Phone Stand", 229_800, 2, null,
                Instant.parse("2026-09-27T10:00:00Z"), List.of(item), Map.of(), 229_800, 0, "Free", "2026-09-phase0", null, null, List.of(), true,
                "Gift wrap please");
        Map<String, Object> spec = Map.of("template", "jharokha_phone_stand@1", "params", Map.of("width_mm", 92), "material", "terracotta_silk");
        when(designs.findVersion(versionId)).thenReturn(Optional.of(new DesignVersionResponse(versionId, designId, 1, null, VersionStatus.ready, spec,
                Map.of("id", "jharokha_phone_stand", "version", 1), assets, Map.of("bounds_mm", List.of(92, 78, 120)), Map.of("passed", true),
                Map.of("print_seconds", 13_200, "extruded_volume_cm3", 51.6), price, null, null, "user", Instant.now())));
        when(catalog.material("terracotta_silk")).thenReturn(Optional.of(new MaterialDto("terracotta_silk", "Terracotta Silk",
                "Silk PLA, copper-terracotta", 1.24, "silk", 463, false, Map.of(), true, 3, Instant.now())));
        AssetFetcher fetcher = url -> url.endsWith(".3mf") ? Optional.of("<model/>".getBytes(StandardCharsets.UTF_8)) : Optional.empty();

        byte[] zip = new PrintPack(designs, catalog, fetcher).build(order);

        Map<String, byte[]> entries = unzip(zip);
        assertThat(entries).containsOnlyKeys("item-1/model.3mf", "item-1/print-sheet.txt");
        assertThat(new String(entries.get("item-1/model.3mf"), StandardCharsets.UTF_8)).isEqualTo("<model/>");
        String sheet = new String(entries.get("item-1/print-sheet.txt"), StandardCharsets.UTF_8);
        assertThat(sheet).contains("AK-000007").contains("Jharokha Phone Stand (design version 1)").contains("jharokha_phone_stand@1")
                .contains("width_mm=92").contains("Terracotta Silk · Silk PLA, copper-terracotta").contains("Finish class:   silk")
                .contains("Quantity:       2").contains("92 × 78 × 120").contains("64 g").contains("3 h 40 m").contains("Gift wrap please")
                .contains("model.3mf (8 bytes)").contains("model.stl MISSING: could not download http://geometry/assets/model.stl");
        assertThat(PrintPack.filename("AK-000007")).isEqualTo("AK-000007-print-pack.zip");
    }

    @Test
    void assetUrlsAreReadDefensively() {
        assertThat(PrintPack.assetUrl(null, "3mf")).isNull();
        assertThat(PrintPack.assetUrl(Map.of("3mf", "not-a-map"), "3mf")).isNull();
        assertThat(PrintPack.assetUrl(Map.of("3mf", Map.of("key", "x")), "3mf")).isNull();
        assertThat(PrintPack.assetUrl(Map.of("3mf", Map.of("url", "http://x/model.3mf")), "3mf")).isEqualTo("http://x/model.3mf");
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }
}
