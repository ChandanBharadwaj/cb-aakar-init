package studio.aakar.api.admin.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The print sheet names everything the outsourced printer needs. */
class PrintSheetTest {

    @Test
    void rendersEveryLine() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("width_mm", 92);
        params.put("arch_cusps", 5);
        PrintSheet.Item item = new PrintSheet.Item("AK-000007", 1, 2, "Jharokha Phone Stand", 1, "jharokha_phone_stand@1", params,
                "Terracotta Silk", "Silk PLA, copper-terracotta", "silk", 2, List.of(92, 78, 120.0), 63.98, 13_200,
                "Terracotta Silk · 92 × 78 × 120 mm · 64 g", "Gift wrap please", List.of("model.3mf (98304 bytes)", "model.stl MISSING: could not download http://x/model.stl"));

        String sheet = PrintSheet.render(item);

        assertThat(sheet).startsWith("AAKAR PRINT SHEET · AK-000007 · item 1 of 2\n");
        assertThat(sheet).contains("Piece:          Jharokha Phone Stand (design version 1)")
                .contains("Template:       jharokha_phone_stand@1")
                .contains("Params:         width_mm=92, arch_cusps=5")
                .contains("Material:       Terracotta Silk · Silk PLA, copper-terracotta")
                .contains("Finish class:   silk")
                .contains("Quantity:       2")
                .contains("Bounds (mm):    92 × 78 × 120")
                .contains("Mass (each):    64 g")
                .contains("Estimated time: 3 h 40 m")
                .contains("Notes:          Gift wrap please")
                .contains("  - model.3mf (98304 bytes)")
                .contains("  - model.stl MISSING: could not download http://x/model.stl")
                .endsWith(PrintSheet.STUDIO_LINE + "\n");
    }

    @Test
    void missingDataIsShownAsDashes() {
        PrintSheet.Item item = new PrintSheet.Item("AK-000008", 1, 1, "Piece", null, null, Map.of(), "indigo_matte", null, null, 1, null, null, null,
                null, null, List.of());
        String sheet = PrintSheet.render(item);
        assertThat(sheet).contains("Content:        —").contains("Hardware:       —");
        assertThat(sheet).contains("Piece:          Piece\n").contains("Template:       unknown (version not found)").contains("Params:         —")
                .contains("Material:       indigo_matte\n").contains("Finish class:   —").contains("Bounds (mm):    —").contains("Mass (each):    —")
                .contains("Estimated time: —").contains("Notes:          —");
    }

    @Test
    void hardwareAndContentRowsNameThePartsAndTheChhaapButNeverTheFileUrl() {
        List<Map<String, Object>> content = List.of(
                Map.of("type", "relief_image", "source", Map.of("upload_id", "3f2b6a1e-8c4d-4e5f-9a0b-1c2d3e4f5a6b",
                        "url", "http://api.internal:8080/media/uploads/abc/3f2b6a1e.png", "format", "png", "origin", "upload"),
                        "anchor", "face", "mode", "emboss", "relief_mm", 0.6),
                Map.of("type", "emboss_text", "text", "Asha", "anchor", "back", "mode", "emboss", "depth_mm", 0.6));
        PrintSheet.Item item = new PrintSheet.Item("AK-000009", 1, 1, "Saathi keychain", 1, "keychain_tag@1", Map.of("shape", "rounded"),
                "Indigo Matte", "Matte PLA, navy", "matte", 3, List.of(45, 27, 3.6), 5.0, 1_800, null, null, List.of("model.3mf (10 bytes)"),
                List.of(new PrintSheet.Hardware("split_ring_25", 1, "Steel split ring 25 mm")), content);

        String sheet = PrintSheet.render(item);

        assertThat(sheet).contains("Content:        photo relief (Chhavi) on face · text (Naam) \"Asha\" on back\n")
                .contains("Hardware:       split_ring_25 × 1 · Steel split ring 25 mm\n")
                .doesNotContain("http").doesNotContain("/media/").doesNotContain("3f2b6a1e");
        // several parts on one line; a part the catalog no longer names still shows its SKU
        PrintSheet.Item two = new PrintSheet.Item("AK-000009", 1, 1, "Magnet", 1, "fridge_magnet@1", Map.of(), "Basic White", null, "matte", 1, null,
                null, null, null, null, List.of(), List.of(new PrintSheet.Hardware("magnet_d10x3", 2, "Neodymium disc magnet 10 × 3 mm"),
                        new PrintSheet.Hardware("gone_sku", 1, null)), List.of());
        assertThat(PrintSheet.render(two)).contains("Hardware:       magnet_d10x3 × 2 · Neodymium disc magnet 10 × 3 mm; gone_sku × 1\n")
                .contains("Content:        —\n");
    }

    @Test
    void contentDescribesEachFeatureByItsLabel() {
        assertThat(PrintSheet.describe(Map.of("type", "relief_image", "anchor", "face", "mode", "deboss"))).isEqualTo("photo relief (Chhavi) on face, deboss");
        assertThat(PrintSheet.describe(Map.of("type", "relief_image", "anchor", "plate", "mode", "lithophane")))
                .isEqualTo("photo relief (Chhavi) on plate, lithophane");
        assertThat(PrintSheet.describe(Map.of("type", "motif", "motif_id", "lotus_border", "anchor", "face", "mode", "deboss")))
                .isEqualTo("motif (Buti) lotus_border on face");
        assertThat(PrintSheet.describe(Map.of("type", "hero_mesh", "anchor", "body", "fit", "longest", "longest_mm", 80, "orientation", "lay_flat",
                "source", Map.of("url", "http://api/media/uploads/x.stl")))).isEqualTo("your own 3D form (Roop) on body, 80 mm longest side, laid flat");
        assertThat(PrintSheet.describe(Map.of("type", "emboss_text", "text", "नमस्ते", "anchor", "back", "mode", "deboss")))
                .isEqualTo("text (Naam) \"नमस्ते\" on back, deboss");
        assertThat(PrintSheet.content(List.of())).isEqualTo("—");
        assertThat(PrintSheet.content(null)).isEqualTo("—");
    }

    @Test
    void durationsAndBoundsFormat() {
        assertThat(PrintSheet.duration(13_200)).isEqualTo("3 h 40 m");
        assertThat(PrintSheet.duration(59 * 60 + 40)).isEqualTo("1 h 00 m");
        assertThat(PrintSheet.duration(5 * 60)).isEqualTo("5 m");
        assertThat(PrintSheet.bounds(List.of(92.5, 78, 120))).isEqualTo("92.5 × 78 × 120");
        assertThat(PrintSheet.bounds(List.of(1))).isEqualTo("—");
    }
}
