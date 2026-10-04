package studio.aakar.api.admin.internal;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import studio.aakar.api.templates.FeatureLabels;

/** The {@code print-sheet.txt} of one order item in the print pack (ADR-0004: printing is outsourced). Pure text. */
final class PrintSheet {

    static final String STUDIO_LINE = "Deliver finished pieces to Aakar Studio, Bengaluru, for QC and packing.";

    private PrintSheet() {
    }

    /**
     * @param templateRef {@code id@version} from the version spec, or null when the version is unknown
     * @param boundsMm {@code [x, y, z]} from the version geometry, or null
     * @param massG per-piece mass from the price snapshot, or null
     * @param printSeconds estimated print time from the estimate or price snapshot, or null
     * @param files one line per model file: included, or missing with the reason
     * @param hardware bought-in parts packed with each piece
     * @param content the spec's content features ({@code design-spec.v1.json#/$defs/feature}); summarised, never with their source URL
     */
    record Item(String orderNumber, int index, int count, String title, Integer versionNo, String templateRef, Map<String, Object> params,
            String materialName, String filament, String finishClass, int qty, List<? extends Number> boundsMm, Double massG,
            Integer printSeconds, String specsLine, String note, List<String> files, List<Hardware> hardware, List<Map<String, Object>> content) {

        Item {
            hardware = hardware == null ? List.of() : List.copyOf(hardware);
            content = content == null ? List.of() : List.copyOf(content);
        }

        /** A sheet without hardware or content (a plain template piece). */
        Item(String orderNumber, int index, int count, String title, Integer versionNo, String templateRef, Map<String, Object> params,
                String materialName, String filament, String finishClass, int qty, List<? extends Number> boundsMm, Double massG,
                Integer printSeconds, String specsLine, String note, List<String> files) {
            this(orderNumber, index, count, title, versionNo, templateRef, params, materialName, filament, finishClass, qty, boundsMm, massG,
                    printSeconds, specsLine, note, files, List.of(), List.of());
        }
    }

    /** A bought-in part per piece: SKU, count and its customer-facing name (null when the catalog no longer knows it). */
    record Hardware(String sku, int qty, String name) {

        String line() {
            return sku + " × " + qty + (name == null || name.isBlank() ? "" : " · " + name);
        }
    }

    static String render(Item item) {
        StringBuilder sheet = new StringBuilder();
        sheet.append("AAKAR PRINT SHEET · ").append(item.orderNumber()).append(" · item ").append(item.index()).append(" of ").append(item.count()).append('\n');
        row(sheet, "Piece", item.title() + (item.versionNo() == null ? "" : " (design version " + item.versionNo() + ")"));
        row(sheet, "Template", item.templateRef() == null ? "unknown (version not found)" : item.templateRef());
        row(sheet, "Params", item.params() == null || item.params().isEmpty() ? "—" : item.params().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
        row(sheet, "Content", content(item.content()));
        row(sheet, "Material", item.materialName() + (item.filament() == null ? "" : " · " + item.filament()));
        row(sheet, "Finish class", item.finishClass() == null ? "—" : item.finishClass());
        row(sheet, "Quantity", String.valueOf(item.qty()));
        row(sheet, "Hardware", item.hardware().isEmpty() ? "—" : item.hardware().stream().map(Hardware::line).collect(Collectors.joining("; ")));
        row(sheet, "Bounds (mm)", bounds(item.boundsMm()));
        row(sheet, "Mass (each)", item.massG() == null ? "—" : Math.round(item.massG()) + " g");
        row(sheet, "Estimated time", item.printSeconds() == null ? "—" : duration(item.printSeconds()));
        row(sheet, "Specs", item.specsLine() == null || item.specsLine().isBlank() ? "—" : item.specsLine());
        row(sheet, "Notes", item.note() == null || item.note().isBlank() ? "—" : item.note());
        sheet.append('\n').append("Files:\n");
        for (String file : item.files()) {
            sheet.append("  - ").append(file).append('\n');
        }
        sheet.append('\n').append(STUDIO_LINE).append('\n');
        return sheet.toString();
    }

    /**
     * {@code photo relief (Chhavi) on face · text (Naam) "Asha" on back}: what content the piece carries and where, by
     * label. The source URL of a photo or model file is never printed.
     */
    static String content(List<Map<String, Object>> features) {
        if (features == null || features.isEmpty()) {
            return "—";
        }
        return features.stream().map(PrintSheet::describe).collect(Collectors.joining(" · "));
    }

    static String describe(Map<String, Object> feature) {
        String type = text(feature.get("type"));
        String label = FeatureLabels.LABELS.getOrDefault(type, type == null ? "content" : type.replace('_', ' '));
        String anchor = text(feature.get("anchor"));
        String where = anchor == null ? "" : " on " + anchor;
        return switch (type == null ? "" : type) {
            case "emboss_text" -> label + " \"" + text(feature.get("text")) + "\"" + where + mode(feature, "emboss");
            case "motif" -> label + " " + text(feature.get("motif_id")) + where + mode(feature, "deboss");
            case "relief_image" -> label + where + mode(feature, "emboss");
            case "hero_mesh" -> label + where + (feature.get("longest_mm") instanceof Number longest ? ", " + number(longest) + " mm longest side" : "")
                    + ("lay_flat".equals(feature.get("orientation")) ? ", laid flat" : "");
            default -> label + where;
        };
    }

    static String bounds(List<? extends Number> bounds) {
        if (bounds == null || bounds.size() < 3) {
            return "—";
        }
        return bounds.stream().limit(3).map(PrintSheet::number).collect(Collectors.joining(" × "));
    }

    static String duration(int seconds) {
        int hours = seconds / 3600;
        int minutes = Math.round((seconds % 3600) / 60f);
        if (minutes == 60) {
            hours++;
            minutes = 0;
        }
        return hours > 0 ? hours + " h " + String.format(Locale.ROOT, "%02d", minutes) + " m" : minutes + " m";
    }

    /** ", deboss" when the feature's mode is not the type's default. */
    private static String mode(Map<String, Object> feature, String defaultMode) {
        String mode = text(feature.get("mode"));
        return mode == null || mode.equals(defaultMode) ? "" : ", " + mode;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String number(Number n) {
        double d = n.doubleValue();
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void row(StringBuilder sheet, String label, String value) {
        sheet.append(String.format(Locale.ROOT, "%-16s%s%n", label + ":", value));
    }
}
