package studio.aakar.api.admin.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;
import studio.aakar.api.catalog.Catalog;
import studio.aakar.api.catalog.HardwareRef;
import studio.aakar.api.catalog.MaterialDto;
import studio.aakar.api.design.DesignVersionResponse;
import studio.aakar.api.design.Designs;
import studio.aakar.api.order.OrderDto;
import studio.aakar.api.order.OrderItemDto;

/**
 * Everything the outsourced printer needs for one order, as a zip built in memory: per item a
 * {@code print-sheet.txt} (with the content it carries and the hardware packed with it) plus {@code model.3mf} and
 * {@code model.stl} downloaded from the version's assets (a file that cannot be fetched is noted on the sheet and
 * skipped), and at the root a {@code packing-list.txt}: the pieces and the bought-in hardware × quantity for the order.
 */
@Component
class PrintPack {

    static final Map<String, String> MODEL_FILES = new LinkedHashMap<>();
    static final String PACKING_LIST = "packing-list.txt";

    static {
        MODEL_FILES.put("3mf", "model.3mf");
        MODEL_FILES.put("stl", "model.stl");
    }

    private final Designs designs;
    private final Catalog catalog;
    private final AssetFetcher assets;

    PrintPack(Designs designs, Catalog catalog, AssetFetcher assets) {
        this.designs = designs;
        this.catalog = catalog;
        this.assets = assets;
    }

    static String filename(String orderNumber) {
        return orderNumber + "-print-pack.zip";
    }

    byte[] build(OrderDto order) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<PrintSheet.Item> sheets = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            int index = 0;
            for (OrderItemDto item : order.items()) {
                index++;
                String dir = "item-" + index + "/";
                List<String> files = new ArrayList<>();
                for (Map.Entry<String, String> model : MODEL_FILES.entrySet()) {
                    String url = assetUrl(item.assets(), model.getKey());
                    Optional<byte[]> bytes = url == null ? Optional.empty() : assets.fetch(url);
                    if (bytes.isPresent()) {
                        entry(zip, dir + model.getValue(), bytes.get());
                        files.add(model.getValue() + " (" + bytes.get().length + " bytes)");
                    } else {
                        files.add(model.getValue() + " MISSING: " + (url == null ? "no " + model.getKey() + " asset recorded on the order"
                                : "could not download " + url));
                    }
                }
                PrintSheet.Item sheet = sheet(order, item, index, files);
                sheets.add(sheet);
                entry(zip, dir + "print-sheet.txt", PrintSheet.render(sheet).getBytes(StandardCharsets.UTF_8));
            }
            entry(zip, PACKING_LIST, packingList(order.number(), sheets).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot build the print pack for " + order.number(), e);
        }
        return out.toByteArray();
    }

    PrintSheet.Item sheet(OrderDto order, OrderItemDto item, int index, List<String> files) {
        Optional<DesignVersionResponse> version = designs.findVersion(item.versionId());
        Optional<MaterialDto> material = catalog.material(item.materialId());
        Map<String, Object> spec = version.map(DesignVersionResponse::spec).orElse(null);
        Map<String, Object> estimate = version.map(DesignVersionResponse::printEstimate).orElse(null);
        Integer printSeconds = estimate != null && estimate.get("print_seconds") instanceof Number n ? n.intValue()
                : item.unitPrice() == null ? null : item.unitPrice().printSeconds();
        Double massG = item.unitPrice() == null ? null : item.unitPrice().massG();
        List<PrintSheet.Hardware> hardware = version.map(DesignVersionResponse::hardware).orElse(List.of()).stream()
                .map(PrintPack::hardware).toList();
        return new PrintSheet.Item(order.number(), index, order.items().size(), item.title(), item.versionNo(),
                spec == null ? null : text(spec.get("template")), spec == null ? null : params(spec.get("params")),
                material.map(MaterialDto::name).orElse(item.materialName() == null ? item.materialId() : item.materialName()),
                material.map(MaterialDto::filament).orElse(null), material.map(MaterialDto::finishClass).orElse(null), item.qty(),
                bounds(version.map(DesignVersionResponse::geometry).orElse(null)), massG, printSeconds, item.specsLine(), order.note(), files,
                hardware, version.map(DesignVersionResponse::features).orElse(List.of()));
    }

    /**
     * {@code packing-list.txt}: one line per piece and the order's bought-in hardware, each SKU's per-piece count times the
     * item quantity, summed across the items.
     */
    static String packingList(String orderNumber, List<PrintSheet.Item> items) {
        StringBuilder list = new StringBuilder();
        list.append("AAKAR PACKING LIST · ").append(orderNumber).append('\n');
        list.append("Pack each piece with the bought-in parts below.").append('\n').append('\n');
        list.append("Pieces:\n");
        for (PrintSheet.Item item : items) {
            list.append("  - item ").append(item.index()).append(" · ").append(item.title())
                    .append(item.versionNo() == null ? "" : " (design version " + item.versionNo() + ")")
                    .append(" × ").append(item.qty()).append(" · ").append(item.materialName()).append('\n');
        }
        Map<String, PrintSheet.Hardware> totals = new LinkedHashMap<>();
        for (PrintSheet.Item item : items) {
            for (PrintSheet.Hardware part : item.hardware()) {
                int count = part.qty() * item.qty();
                totals.merge(part.sku(), new PrintSheet.Hardware(part.sku(), count, part.name()),
                        (a, b) -> new PrintSheet.Hardware(a.sku(), a.qty() + b.qty(), a.name() != null ? a.name() : b.name()));
            }
        }
        list.append('\n').append("Hardware:");
        if (totals.isEmpty()) {
            list.append(" none\n");
        } else {
            list.append('\n');
            totals.values().forEach(part -> list.append("  - ").append(part.line()).append('\n'));
        }
        return list.toString();
    }

    private static PrintSheet.Hardware hardware(HardwareRef ref) {
        return new PrintSheet.Hardware(ref.sku(), ref.qty() == null ? 1 : ref.qty(), ref.name());
    }

    @SuppressWarnings("unchecked")
    static String assetUrl(Map<String, Object> assets, String kind) {
        if (assets == null || !(assets.get(kind) instanceof Map<?, ?> asset)) {
            return null;
        }
        Object url = ((Map<String, Object>) asset).get("url");
        return url instanceof String s && !s.isBlank() ? s : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> params(Object params) {
        return params instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<? extends Number> bounds(Map<String, Object> geometry) {
        if (geometry == null || !(geometry.get("bounds_mm") instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof Number))) {
            return null;
        }
        return (List<? extends Number>) list;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
