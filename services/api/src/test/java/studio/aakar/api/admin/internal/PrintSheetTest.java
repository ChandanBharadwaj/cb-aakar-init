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
        assertThat(sheet).contains("Piece:          Piece\n").contains("Template:       unknown (version not found)").contains("Params:         —")
                .contains("Material:       indigo_matte\n").contains("Finish class:   —").contains("Bounds (mm):    —").contains("Mass (each):    —")
                .contains("Estimated time: —").contains("Notes:          —");
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
