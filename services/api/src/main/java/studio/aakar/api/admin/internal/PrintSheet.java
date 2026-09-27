package studio.aakar.api.admin.internal;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
     */
    record Item(String orderNumber, int index, int count, String title, Integer versionNo, String templateRef, Map<String, Object> params,
            String materialName, String filament, String finishClass, int qty, List<? extends Number> boundsMm, Double massG,
            Integer printSeconds, String specsLine, String note, List<String> files) {
    }

    static String render(Item item) {
        StringBuilder sheet = new StringBuilder();
        sheet.append("AAKAR PRINT SHEET · ").append(item.orderNumber()).append(" · item ").append(item.index()).append(" of ").append(item.count()).append('\n');
        row(sheet, "Piece", item.title() + (item.versionNo() == null ? "" : " (design version " + item.versionNo() + ")"));
        row(sheet, "Template", item.templateRef() == null ? "unknown (version not found)" : item.templateRef());
        row(sheet, "Params", item.params() == null || item.params().isEmpty() ? "—" : item.params().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
        row(sheet, "Material", item.materialName() + (item.filament() == null ? "" : " · " + item.filament()));
        row(sheet, "Finish class", item.finishClass() == null ? "—" : item.finishClass());
        row(sheet, "Quantity", String.valueOf(item.qty()));
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

    private static String number(Number n) {
        double d = n.doubleValue();
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static void row(StringBuilder sheet, String label, String value) {
        sheet.append(String.format(Locale.ROOT, "%-16s%s%n", label + ":", value));
    }
}
