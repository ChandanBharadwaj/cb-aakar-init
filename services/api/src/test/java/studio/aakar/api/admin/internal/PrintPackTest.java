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
import studio.aakar.api.catalog.HardwareRef;
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
        assertThat(entries).containsOnlyKeys("item-1/model.3mf", "item-1/print-sheet.txt", "packing-list.txt");
        assertThat(new String(entries.get("packing-list.txt"), StandardCharsets.UTF_8)).startsWith("AAKAR PACKING LIST · AK-000007\n")
                .contains("  - item 1 · Jharokha Phone Stand (design version 1) × 2 · Terracotta Silk\n").endsWith("Hardware: none\n");
        assertThat(new String(entries.get("item-1/model.3mf"), StandardCharsets.UTF_8)).isEqualTo("<model/>");
        String sheet = new String(entries.get("item-1/print-sheet.txt"), StandardCharsets.UTF_8);
        assertThat(sheet).contains("AK-000007").contains("Jharokha Phone Stand (design version 1)").contains("jharokha_phone_stand@1")
                .contains("width_mm=92").contains("Terracotta Silk · Silk PLA, copper-terracotta").contains("Finish class:   silk")
                .contains("Quantity:       2").contains("92 × 78 × 120").contains("64 g").contains("3 h 40 m").contains("Gift wrap please")
                .contains("model.3mf (8 bytes)").contains("model.stl MISSING: could not download http://geometry/assets/model.stl");
        assertThat(PrintPack.filename("AK-000007")).isEqualTo("AK-000007-print-pack.zip");
    }

    @Test
    void sheetsCarryHardwareAndContentAndThePackingListAddsUpTheParts() throws IOException {
        UUID keychainVersion = UUID.randomUUID();
        UUID magnetVersion = UUID.randomUUID();
        UUID designId = UUID.randomUUID();
        Map<String, Object> assets = Map.of("3mf", Map.of("url", "http://geometry/assets/k.3mf"), "stl", Map.of("url", "http://geometry/assets/k.stl"));
        Map<String, Object> relief = Map.of("type", "relief_image", "anchor", "face", "mode", "emboss",
                "source", Map.of("upload_id", UUID.randomUUID().toString(), "url", "http://api.internal:8080/media/uploads/f/p.png"));
        Map<String, Object> keychainSpec = Map.of("template", "keychain_tag@1", "params", Map.of("shape", "rounded"), "material", "indigo_matte",
                "features", List.of(relief, Map.of("type", "relief_image", "anchor", "back", "mode", "deboss",
                        "source", Map.of("upload_id", UUID.randomUUID().toString(), "url", "http://api.internal:8080/media/uploads/f/q.png"))));
        Map<String, Object> magnetSpec = Map.of("template", "fridge_magnet@1", "params", Map.of("magnet_count", 2), "material", "indigo_matte",
                "features", List.of(relief));
        when(designs.findVersion(keychainVersion)).thenReturn(Optional.of(new DesignVersionResponse(keychainVersion, designId, 1, null, VersionStatus.ready,
                keychainSpec, null, assets, null, null, null, null, "keychain",
                List.of(new HardwareRef("split_ring_25", 1, "Steel split ring 25 mm")), null, null, "user", Instant.now())));
        when(designs.findVersion(magnetVersion)).thenReturn(Optional.of(new DesignVersionResponse(magnetVersion, designId, 2, null, VersionStatus.ready,
                magnetSpec, null, assets, null, null, null, null, "fridge_magnet",
                List.of(new HardwareRef("magnet_d10x3", 2, "Neodymium disc magnet 10 × 3 mm"), new HardwareRef("split_ring_25", 1, "Steel split ring 25 mm")),
                null, null, "user", Instant.now())));
        when(catalog.material("indigo_matte")).thenReturn(Optional.of(new MaterialDto("indigo_matte", "Indigo Matte", "Matte PLA, navy", 1.24, "matte",
                420, false, Map.of(), true, 6, Instant.now())));
        OrderItemDto keychains = new OrderItemDto(UUID.randomUUID(), designId, keychainVersion, 1, "Saathi keychain", null, "indigo_matte",
                "Indigo Matte", 3, null, 74_700, assets);
        OrderItemDto magnets = new OrderItemDto(UUID.randomUUID(), designId, magnetVersion, 2, "Chumbak magnet", null, "indigo_matte",
                "Indigo Matte", 2, null, 59_800, assets);
        OrderDto order = new OrderDto(UUID.randomUUID(), "AK-000011", OrderStatus.queued, OrderStage.queued, "Saathi keychain + 1 more", 134_500, 5,
                null, Instant.parse("2026-09-27T10:00:00Z"), List.of(keychains, magnets), Map.of(), 134_500, 0, "Free", "2026-10-carriers", null, null,
                List.of(), true, null);
        AssetFetcher fetcher = url -> Optional.of("x".getBytes(StandardCharsets.UTF_8));

        Map<String, byte[]> entries = unzip(new PrintPack(designs, catalog, fetcher).build(order));

        assertThat(entries).containsKeys("item-1/print-sheet.txt", "item-2/print-sheet.txt", "packing-list.txt");
        String first = new String(entries.get("item-1/print-sheet.txt"), StandardCharsets.UTF_8);
        assertThat(first).contains("Hardware:       split_ring_25 × 1 · Steel split ring 25 mm")
                .contains("Content:        photo relief (Chhavi) on face · photo relief (Chhavi) on back, deboss")
                .doesNotContain("/media/").doesNotContain("api.internal");
        assertThat(new String(entries.get("item-2/print-sheet.txt"), StandardCharsets.UTF_8))
                .contains("Hardware:       magnet_d10x3 × 2 · Neodymium disc magnet 10 × 3 mm; split_ring_25 × 1 · Steel split ring 25 mm");
        String packing = new String(entries.get("packing-list.txt"), StandardCharsets.UTF_8);
        assertThat(packing).startsWith("AAKAR PACKING LIST · AK-000011\n")
                .contains("  - item 1 · Saathi keychain (design version 1) × 3 · Indigo Matte\n")
                .contains("  - item 2 · Chumbak magnet (design version 2) × 2 · Indigo Matte\n")
                // 3 keychains × 1 ring + 2 magnets × 1 ring; 2 magnets × 2 magnets each
                .contains("Hardware:\n  - split_ring_25 × 5 · Steel split ring 25 mm\n  - magnet_d10x3 × 4 · Neodymium disc magnet 10 × 3 mm\n");
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
